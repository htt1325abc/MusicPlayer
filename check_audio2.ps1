Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class AudioCheck2 {
  static Guid CLSID_MMDeviceEnumerator = new Guid("BCDE0395-E52F-467C-8E3D-C4579291692E");
  static Guid IID_IAudioEndpointVolume = new Guid("5CDF2C82-841E-4546-9722-0CF74078229A");
  static Guid IID_IAudioSessionManager2 = new Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F");
  static Guid IID_IAudioSessionEnumerator = new Guid("A2B1A1D9-4DB3-425D-A2B2-BD1CB0AFE372");
  static Guid IID_ISimpleAudioVolume = new Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8");

  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
  interface IMMDeviceEnumerator { int EnumAudioEndpoints(int d, int s, out IntPtr list); int GetDefaultAudioEndpoint(int d, int r, out IntPtr dev); }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("D666063F-1587-4E43-81F1-B948E807363F")]
  interface IMMDevice { int Activate(ref Guid iid, int ctx, IntPtr p, out IntPtr iface); int OpenPropertyStore(int a, out IntPtr b); int GetId(out IntPtr id); int GetState(out int s); }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("5CDF2C82-841E-4546-9722-0CF74078229A")]
  interface IAudioEndpointVolume { int RegisterControlChangeNotify(IntPtr p); int UnregisterControlChangeNotify(IntPtr p); int GetChannelCount(out int c); int SetMasterVolumeLevel(float v, ref Guid g); int SetMasterVolumeLevelScalar(float v, ref Guid g); int GetMasterVolumeLevel(out float v); int GetMasterVolumeLevelScalar(out float v); int SetChannelVolumeLevel(uint i, float v, ref Guid g); int SetChannelVolumeLevelScalar(uint i, float v, ref Guid g); int GetChannelVolumeLevel(uint i, out float v); int GetChannelVolumeLevelScalar(uint i, out float v); int SetMute(bool m, ref Guid g); int GetMute(out bool m); }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F")]
  interface IAudioSessionManager2 { int GetAudioSessionEnumerator(out IntPtr s); }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("A2B1A1D9-4DB3-425D-A2B2-BD1CB0AFE372")]
  interface IAudioSessionEnumerator { int GetCount(out int n); int GetSession(int i, out IntPtr s); }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8")]
  interface ISimpleAudioVolume { int SetMasterVolume(float v, Guid g); int GetMasterVolume(out float v); int SetMute(bool m, Guid g); int GetMute(out bool m); }

  public static void Dump() {
    Type t = Type.GetTypeFromCLSID(CLSID_MMDeviceEnumerator);
    var e = (IMMDeviceEnumerator)Activator.CreateInstance(t);
    IntPtr devp; e.GetDefaultAudioEndpoint(0, 0, out devp);
    var dev = (IMMDevice)Marshal.GetObjectForIUnknown(devp);
    IntPtr idp; dev.GetId(out idp);
    Console.WriteLine("DefaultPlaybackDevice=" + Marshal.PtrToStringUni(idp));
    Guid iid = IID_IAudioEndpointVolume; IntPtr iface; dev.Activate(ref iid, 0, IntPtr.Zero, out iface);
    var vol = (IAudioEndpointVolume)Marshal.GetObjectForIUnknown(iface);
    float scalar; vol.GetMasterVolumeLevelScalar(out scalar); bool mute; vol.GetMute(out mute);
    Console.WriteLine("EndpointVolume=" + (scalar*100).ToString("0") + "% Mute=" + mute);

    IntPtr mgrp; dev.Activate(ref IID_IAudioSessionManager2, 0, IntPtr.Zero, out mgrp);
    var mgr = (IAudioSessionManager2)Marshal.GetObjectForIUnknown(mgrp);
    IntPtr enp; mgr.GetAudioSessionEnumerator(out enp);
    var en = (IAudioSessionEnumerator)Marshal.GetObjectForIUnknown(enp);
    int cnt; en.GetCount(out cnt);
    for (int i = 0; i < cnt; i++) {
      IntPtr sp; en.GetSession(i, out sp);
      var sc = (ISimpleAudioVolume)Marshal.GetObjectForIUnknown(sp);
      float v; sc.GetMasterVolume(out v); bool m; sc.GetMute(out m);
      Console.WriteLine("Session[" + i + "] Volume=" + (v*100).ToString("0") + "% Mute=" + m);
    }
  }
}
"@
[AudioCheck2]::Dump()
