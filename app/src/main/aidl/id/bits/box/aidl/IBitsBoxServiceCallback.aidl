package id.bits.box.aidl;

import id.bits.box.aidl.SpeedDisplayData;
import id.bits.box.aidl.TrafficData;

oneway interface IBitsBoxServiceCallback {
  void stateChanged(int state, String profileName, String msg);
  void cbSpeedUpdate(in SpeedDisplayData stats);
  void cbTrafficUpdate(in TrafficData stats);
  void cbSelectorUpdate(long id);
}
