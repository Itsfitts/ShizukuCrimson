package af.shizuku.server;

interface IShizukuServiceConnection {

    oneway void connected(IBinder service);

    oneway void died();
}
