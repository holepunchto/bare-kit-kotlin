-keep class to.holepunch.bare.kit.Native {
  native <methods>;
}

-keep class to.holepunch.bare.kit.BareKitException$Failed {
  <init>(int);
}

-keepclassmembers class to.holepunch.bare.kit.Worklet {
  private void onExit(int);
}

-keepclassmembers class to.holepunch.bare.kit.Ipc {
  private void onPoll(int);
}
