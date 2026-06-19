package rikka.shizuku.server;

import moe.shizuku.server.IRemoteProcess;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import java.io.IOException;

public class MockRemoteProcess extends IRemoteProcess.Stub {
    private final int exitCode;
    private final String errorOutput;
    private final String standardOutput;

    public MockRemoteProcess(int exitCode, String errorOutput, String standardOutput) {
        this.exitCode = exitCode;
        this.errorOutput = errorOutput;
        this.standardOutput = standardOutput;
    }

    private static ParcelFileDescriptor stringToPfd(String text) {
        if (text == null) text = "";
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            try (ParcelFileDescriptor.AutoCloseOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                outputStream.write(text.getBytes());
                outputStream.flush();
            }
            return pipe[0];
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor getOutputStream() throws RemoteException {
        return null;
    }

    @Override
    public ParcelFileDescriptor getInputStream() throws RemoteException {
        return stringToPfd(standardOutput);
    }

    @Override
    public ParcelFileDescriptor getErrorStream() throws RemoteException {
        return stringToPfd(errorOutput);
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
