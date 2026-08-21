package id.bits.box.aidl;

import id.bits.box.aidl.IBitsBoxServiceCallback;

interface IBitsBoxService {
  int getState();
  String getProfileName();

  void registerCallback(in IBitsBoxServiceCallback cb, int id);
  oneway void unregisterCallback(in IBitsBoxServiceCallback cb);

  int urlTest();
}
