package rikka.shizuku.server;

import af.shizuku.server.IRemoteProcess;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import java.io.IOException;

public class MockRemoteProcess extends IRemoteProcess.Stub {
    private static final String TAG = "MockRemoteProcess";
    private final int exitCode;
    private final String errorOutput;
    private final String standardOutput;

    public MockRemoteProcess(int exitCode, String errorOutput, String standardOutput) {
        this.exitCode = exitCode;
        this.errorOutput = errorOutput;
        this.standardOutput = standardOutput;
    }

    private ParcelFileDescriptor createPfdFromString(String content) {
        if (content == null) {
            return null;
        }
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor readSide = pipe[0];
            ParcelFileDescriptor writeSide = pipe[1];

            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {
                    out.write(content.getBytes());
                    out.flush();
                } catch (IOException e) {
                    Log.e(TAG, "Error writing mock process output", e);
                }
            }).start();

            return readSide;
        } catch (IOException e) {
            Log.e(TAG, "Failed to create pipe for mock process", e);
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor getOutputStream() throws RemoteException {
        return null;
    }

    @Override
    public ParcelFileDescriptor getInputStream() throws RemoteException {
        return createPfdFromString(standardOutput);
    }

    @Override
    public ParcelFileDescriptor getErrorStream() throws RemoteException {
        return createPfdFromString(errorOutput);
    }

    @Override
    public int waitFor() throws RemoteException {
        return exitCode;
    }

    @Override
    public int exitValue() throws RemoteException {
        return exitCode;
    }

    @Override
    public void destroy() throws RemoteException {
        // No-op for mock process
    }

    @Override
    public boolean alive() throws RemoteException {
        return false;
    }

    @Override
    public boolean waitForTimeout(long timeout, String unitName) throws RemoteException {
        return true;
    }
}
