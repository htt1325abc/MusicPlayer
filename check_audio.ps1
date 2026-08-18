Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class AudioCheck {
  [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")] class MMDeviceEnumeratorComObject { }
  static Guid IID_IAudioEndpointVolume = new Guid("5CDF2C82-841E-4546-9722-0CF74078229A");
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
  interface IMMDeviceEnumerator { int GetDefaultAudioEndpoint(int d, int r, out IMMDevice dev); }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("D666063F-1587-4E43-81F1-B948E807363F")]
  interface IMMDevice {
    int Activate(ref Guid iid, int ctx, IntPtr p, out IntPtr iface);
    int OpenPropertyStore(int a, out IntPtr b);
    int GetId(out IntPtr id);
    int GetState(out int s);
  }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("5CDF2C82-841E-4546-9722-0CF74078229A")]
  interface IAudioEndpointVolume { int RegisterControlChangeNotify(IntPtr p); int UnregisterControlChangeNotify(IntPtr p); int GetChannelCount(out int c); int SetMasterVolumeLevel(float v, ref Guid g); int SetMasterVolumeLevelScalar(float v, ref Guid g); int GetMasterVolumeLevel(out float v); int GetMasterVolumeLevelScalar(out float v); int SetChannelVolumeLevel(uint i, float v, ref Guid g); int SetChannelVolumeLevelScalar(uint i, float v, ref Guid g); int GetChannelVolumeLevel(uint i, out float v); int GetChannelVolumeLevelScalar(uint i, out float v); int SetMute(bool m, ref Guid g); int GetMute(out bool m); }
  public static void Dump() {
    var e = (IMMDeviceEnumerator)(new MMDeviceEnumeratorComObject());
    IMMDevice dev; e.GetDefaultAudioEndpoint(0, 0, out dev);
    IntPtr idp; dev.GetId(out idp);
    string id = Marshal.PtrToStringUni(idp);
    Console.WriteLine("DefaultPlaybackDevice=" + id);
    Guid iid = IID_IAudioEndpointVolume; IntPtr iface; dev.Activate(ref iid, 0, IntPtr.Zero, out iface);
    var vol = (IAudioEndpointVolume)Marshal.GetObjectForIUnknown(iface);
    float scalar; vol.GetMasterVolumeLevelScalar(out scalar); bool mute; vol.GetMute(out mute);
    Console.WriteLine("MasterVolumeScalar=" + (scalar*100).ToString("0") + "% Mute=" + mute);
  }
}
"@
[AudioCheck]::Dump()
