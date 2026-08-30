package org.opencv.engine;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface OpenCVEngineInterface extends IInterface {
    int getEngineVersion() throws RemoteException;

    String getLibPathByVersion(String str) throws RemoteException;

    String getLibraryList(String str) throws RemoteException;

    boolean installVersion(String str) throws RemoteException;

    public static abstract class Stub extends Binder implements OpenCVEngineInterface {
        private static final String DESCRIPTOR = "org.opencv.engine.OpenCVEngineInterface";
        static final int TRANSACTION_getEngineVersion = 1;
        static final int TRANSACTION_getLibPathByVersion = 2;
        static final int TRANSACTION_getLibraryList = 4;
        static final int TRANSACTION_installVersion = 3;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static OpenCVEngineInterface asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && (iin instanceof OpenCVEngineInterface)) {
                return (OpenCVEngineInterface) iin;
            }
            return new Proxy(obj);
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case 1:
                    data.enforceInterface(DESCRIPTOR);
                    int _result = getEngineVersion();
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg0 = data.readString();
                    String _result2 = getLibPathByVersion(_arg0);
                    reply.writeNoException();
                    reply.writeString(_result2);
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg02 = data.readString();
                    boolean _result3 = installVersion(_arg02);
                    reply.writeNoException();
                    reply.writeInt(_result3 ? 1 : 0);
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    String _arg03 = data.readString();
                    String _result4 = getLibraryList(_arg03);
                    reply.writeNoException();
                    reply.writeString(_result4);
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements OpenCVEngineInterface {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            @Override // org.opencv.engine.OpenCVEngineInterface
            public int getEngineVersion() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.opencv.engine.OpenCVEngineInterface
            public String getLibPathByVersion(String version) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(version);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.opencv.engine.OpenCVEngineInterface
            public boolean installVersion(String version) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(version);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override // org.opencv.engine.OpenCVEngineInterface
            public String getLibraryList(String version) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(version);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
