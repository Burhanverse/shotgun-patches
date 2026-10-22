package dev.jason.gboardpatches.extension.flowmode;

public final class GboardFlowModePointerCallback {
    private final GboardFlowModeRequestBridge bridge;
    private final Object processor,snapshot;
    GboardFlowModePointerCallback(GboardFlowModeRequestBridge bridge,Object processor,Object snapshot) {
        this.bridge=bridge;this.processor=processor;this.snapshot=snapshot;
    }
    public void invoke(long handle) { bridge.scheduledNative(processor,snapshot,handle); }
}
