package rikka.shizuku.server;

import af.shizuku.server.IRemoteProcess;
import android.os.RemoteException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class MockRemoteProcess extends IRemoteProcess.Stub {
    private final int exitCode;
    private final String errorOutput;
    private final String standardOutput;

    public MockRemoteProcess(int exitCode, String errorOutput, String standardOutput) {
        this.exitCode = exitCode;
        this.errorOutput = errorOutput;
        this.standardOutput = standardOutput;
    }

    @Override
    public InputStream getInputStream() throws RemoteException {
        return new ByteArrayInputStream(standardOutput.getBytes());
    }

    @Override
    public InputStream getErrorStream() throws RemoteException {
        return new ByteArrayInputStream(errorOutput.getBytes());
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
}
