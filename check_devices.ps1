Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class AudioDevs {
  static Guid CLSID_MMDeviceEnumerator = new Guid("BCDE0395-E52F-467C-8E3D-C4579291692E");
  static Guid PKEY_Device_FriendlyName = new Guid("a45c254e-df1c-4efd-8020-67d146a850e0");
  [StructLayout(LayoutKind.Sequential)]
  struct PROPVARIANT { public ushort vt; public ushort w1; public ushort w2; public ushort w3; public IntPtr p; public int x1; public int x2; }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
  interface IMMDeviceEnumerator { int EnumAudioEndpoints(int d, int s, out IntPtr list); int GetDefaultAudioEndpoint(int d, int r, out IntPtr dev); }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("0BD7A1BE-7A1A-44DB-8397-CC5392387B5E")]
  interface IMMDeviceCollection { int GetCount(out int c); int Item(int i, out IntPtr dev); }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("D666063F-1587-4E43-81F1-B948E807363F")]
  interface IMMDevice { int Activate(ref Guid iid, int ctx, IntPtr p, out IntPtr iface); int OpenPropertyStore(int a, out IntPtr b); int GetId(out IntPtr id); int GetState(out int s); }
  [ComImport, InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid("886d8eeb-8cf2-4446-8d02-cdba1dbdcf99")]
  interface IPropertyStore { int GetCount(out int c); int GetAt(int i, out IntPtr key); int GetValue(ref Guid key, out PROPVARIANT v); int SetValue(ref Guid key, ref PROPVARIANT v); int Commit(); }
  [DllImport("ole32.dll")] static extern int PropVariantToString(PROPVARIANT p, System.Text.StringBuilder s, int c);

  public static void List() {
    Type t = Type.GetTypeFromCLSID(CLSID_MMDeviceEnumerator);
    var e = (IMMDeviceEnumerator)Activator.CreateInstance(t);
    IntPtr listp; e.EnumAudioEndpoints(0, 0, out listp);
    var coll = (IMMDeviceCollection)Marshal.GetObjectForIUnknown(listp);
    int n; coll.GetCount(out n);
    IntPtr defp; e.GetDefaultAudioEndpoint(0, 0, out defp);
    var def = (IMMDevice)Marshal.GetObjectForIUnknown(defp);
    IntPtr defidp; def.GetId(out defidp);
    string defid = Marshal.PtrToStringUni(defidp);
    for (int i = 0; i < n; i++) {
      IntPtr dp; coll.Item(i, out dp);
      var dev = (IMMDevice)Marshal.GetObjectForIUnknown(dp);
      IntPtr idp; dev.GetId(out idp); string id = Marshal.PtrToStringUni(idp);
      int st; dev.GetState(out st);
      IntPtr ps; dev.OpenPropertyStore(0, out ps);
      var store = (IPropertyStore)Marshal.GetObjectForIUnknown(ps);
      Guid fname = PKEY_Device_FriendlyName;
      PROPVARIANT pv; store.GetValue(ref fname, out pv);
      var sb = new System.Text.StringBuilder(256);
      PropVariantToString(pv, sb, 256);
      string isDefault = (id == defid) ? "  <== DEFAULT" : "";
      Console.WriteLine("state=" + st + " | " + sb.ToString() + " | " + id + isDefault);
    }
  }
}
"@
[AudioDevs]::List()
