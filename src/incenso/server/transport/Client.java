package incenso.server.transport;

import incenso.common.*;
import incenso.server.util.Promise;
import incenso.server.util.RemoteException;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.locks.ReentrantLock;

public class Client {
    private final Socket io;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    private final ReentrantLock uploadLock = new ReentrantLock();
    private final ReentrantLock synchronizeLock = new ReentrantLock();

    private long storage = 0, ram = 0;
    private int processors = 0;

    private final IncensoServer server;

    private Thread keepaliveThread;

    public Client(IncensoServer parent, Socket io) throws RemoteException {
        this.io = io;
        this.server = parent;

        try {
            out = new ObjectOutputStream(io.getOutputStream());
            in = new ObjectInputStream(io.getInputStream());

            resync().unwrap(x -> {
                keepaliveThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while(!keepaliveThread.isInterrupted()) {
                            Client.this.isAlive()
                                    .orElse(x -> { keepaliveThread.interrupt(); })
                                    .resolve();

                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    }
                }, "Client keepalive thread.");

                keepaliveThread.start();
            }).orElse(x -> { throw new RuntimeException(x); });
        } catch(IOException e) {
            throw new RemoteException("Socket manipulation exception.", e);
        } catch(RuntimeException e) {
            throw (RuntimeException) e.getCause();
        } catch(Exception e) {
            throw new RemoteException("Unhandled error.", e);
        }
    }

    public long getStorageKilobytes() {
        return storage;
    }

    public long getRAMKiB() {
        return ram;
    }

    public int getCPUs() {
        return processors;
    }

    public Promise<Boolean, RemoteException> isAlive() {
        return new Promise<Boolean, RemoteException>() {
            @Override
            protected void onResolve() {
                uploadLock.unlock();
            }

            @Override
            protected void process() {
                Packet response = null;

                uploadLock.lock();

                try {
                    out.writeObject(new PacketKeepalive());
                    out.flush();

                    response = (Packet) in.readObject();
                } catch(Exception e) {
                    fail(new RemoteException("I/O Exception.", e));
                    // Assume we've been disconnected.
                    server.onDisconnect().broadcast(Client.this);
                    server.clientUnlink(Client.this);
                    return;
                }

                if(response != null && response.getType() == PacketType.PACKET_KEEPALIVE)
                    finish(true);
                else {
                    fail(new RemoteException("Invalid packet."));
                    // Assume we've been disconnected.
                    server.onDisconnect().broadcast(Client.this);
                    server.clientUnlink(Client.this);
                }
            }
        };
    }

    public Promise<Boolean, RemoteException> forceGC() {
        return new Promise<Boolean, RemoteException>() {
            @Override
            protected void onResolve() {
                uploadLock.unlock();
            }

            @Override
            protected void process() {
                Packet response = null;

                uploadLock.lock();

                try {
                    out.writeObject(new PacketGC());
                    out.flush();
                } catch(Exception e) {
                    fail(new RemoteException("I/O Exception.", e));
                    return;
                }

                finish(true);
            }
        };
    }

    public void setConnectionTimeout(int millis) throws RemoteException {
        try {
            io.setSoTimeout(millis);
        } catch(SocketException e) {
            throw new RemoteException("Couldn't set the connection timeout.", e);
        } catch(IllegalArgumentException e) {
            throw new RemoteException("millis <= 0", e);
        }
    }

    public Promise<Serializable, RemoteException> schedule(Class<? extends CodeChunk> clz, Serializable param) {
        return new Promise<Serializable, RemoteException>() {
            @Override
            protected void onResolve() {
                uploadLock.unlock();
            }

            @Override
            protected void process() {
                Serializable obj = null;

                uploadLock.lock();

                try {
                    out.writeObject(new PacketExecute(clz.getName(), clz));
                    out.flush();

                    if (!in.readBoolean()) {
                        fail(new RemoteException("Couldn't load the class.", (Throwable) in.readObject()));
                        return;
                    }

                    out.writeObject(param);
                    out.flush();

                    if (!in.readBoolean()) {
                        fail(new RemoteException("Execution failed.", (Throwable) in.readObject()));
                        return;
                    }

                    obj = (Serializable) in.readObject();
                } catch(IOException e) {
                    fail(new RemoteException("I/O exception.", e));
                    return;
                } catch (Exception e) {
                    fail(new RemoteException("Unhandled error.", e));
                    return;
                }

                finish(obj);
            }
        };
    }

    public Promise<Boolean, RemoteException> scheduleNew(Class<? extends CodeChunk> clz, Serializable param) {
        return new Promise<Boolean, RemoteException>() {
            @Override
            protected void onResolve() {
                uploadLock.unlock();
            }

            @Override
            protected void process() {
                uploadLock.lock();

                // TODO: Make it more readable, but how?
                upload("_internal_scheduleNew", clz).unwrap(x ->
                        schedule(clz, param).unwrap(y ->
                                unlink("_internal_scheduleNew").unwrap(this::finish)
                                        .orElse(this::fail).resolve()
                        ).orElse(this::fail).resolve()
                ).orElse(this::fail).resolve();
            }
        };
    }

    public Promise<Boolean, RemoteException> upload(String moduleName, Class<?> clz) {
        return new Promise<Boolean, RemoteException>() {
            @Override
            protected void onResolve() {
                uploadLock.unlock();
            }

            @Override
            protected void process() {
                boolean uploadStatus;

                uploadLock.lock();

                try {
                    out.writeObject(new PacketInject(clz.getName(), moduleName, clz));
                    out.flush();
                    uploadStatus = in.readBoolean();
                } catch(Exception e) {
                    fail(new RemoteException("I/O exception", e));
                    return;
                }

                if(!uploadStatus) {
                    fail(new RemoteException("Injection wasn't acknowledged by the server."));
                    return;
                }

                finish(true);
            }
        };
    }

    public Promise<Boolean, RemoteException> unlink(String moduleName) {
        return new Promise<Boolean, RemoteException>() {
            @Override
            protected void onResolve() {
                uploadLock.unlock();
            }

            @Override
            protected void process() {
                uploadLock.lock();

                try {
                    out.writeObject(new PacketUnlink(moduleName));
                    out.flush();

                    if(in.readBoolean()) {
                        // Trigger a GC cycle to ensure the classes have been unlinked.
                        forceGC().orElse(this::fail).unwrap(this::finish).resolve();
                    } else {
                        fail(new RemoteException("Couldn't unlink group."));
                    }
                } catch(Exception e) {
                    fail(new RemoteException("I/O exception", e));
                }
            }
        };
    }

    public Promise<Boolean, RemoteException> disconnect() {
        return new Promise<Boolean, RemoteException>() {
            @Override
            protected void onResolve() {
                uploadLock.unlock();
            }

            @Override
            protected void process() {
                uploadLock.lock();

                try {
                    out.writeObject(new PacketGoodbye());
                    out.flush();

                    out.close();
                    in.close();

                    io.close();

                    server.onDisconnect().broadcast(Client.this);
                    server.clientUnlink(Client.this);
                } catch(Exception e) {
                    fail(new RemoteException("I/O Exception.", e));
                    return;
                }

                finish(true);
            }
        };
    }

    public Promise<Boolean, RemoteException> resync() {
        return new Promise<Boolean, RemoteException>() {
            @Override
            protected void onResolve() {
                uploadLock.unlock();
                synchronizeLock.unlock();
            }

            @Override
            protected void process() {
                synchronizeLock.lock();
                uploadLock.lock();

                try {
                    out.writeObject(new PacketHandshake(
                            System.getProperty("java.version"),
                            System.getProperty("java.vendor"),
                            -1, -1, -1));
                    out.flush();

                    PacketHandshake obj = ((PacketHandshake) in.readObject());
                    Client.this.processors = obj.getCPUs();
                    Client.this.storage = obj.getStorageSize();
                    Client.this.ram = obj.getMaxRAM();
                } catch(IOException e) {
                    fail(new RemoteException("I/O exception.", e));
                    return;
                } catch(ClassNotFoundException e) {
                    fail(new RemoteException("Class not found on the remote machine.", e));
                    return;
                } catch(Exception e) {
                    fail(new RemoteException("Unhandled exception.", e));
                    return;
                }

                finish(true);
            }
        };
    }
}
