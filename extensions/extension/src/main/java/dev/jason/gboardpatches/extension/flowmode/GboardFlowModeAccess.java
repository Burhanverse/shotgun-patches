package dev.jason.gboardpatches.extension.flowmode;

public class GboardFlowModeAccess {
    public android.view.inputmethod.EditorInfo extensionEditor(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: extensionEditor");
    }
    public Object extensionCoordinator(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: extensionCoordinator");
    }
    public boolean active(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: active");
    }
    public int color(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: color");
    }
    public Object actions(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: actions");
    }
    public Object payload(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: payload");
    }
    public int eventKeyCode(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: eventKeyCode");
    }
    public Object requestType(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: requestType");
    }
    public java.util.concurrent.atomic.AtomicBoolean javaState(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: javaState");
    }
    public Object currentProcessor(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: currentProcessor");
    }
    public int prefilterCid(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: prefilterCid");
    }
    public Object prefilterModule(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: prefilterModule");
    }
    public Object prefilterRequest(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: prefilterRequest");
    }
    public int continuationCid(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: continuationCid");
    }
    public Object continuationModule(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: continuationModule");
    }
    public Object continuationRequest(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: continuationRequest");
    }
    public Object continuationProcessor(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: continuationProcessor");
    }
    public Object taskProcessor(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: taskProcessor");
    }
    public int nativeResponseCid(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: nativeResponseCid");
    }
    public Object nativeResponseProcessor(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: nativeResponseProcessor");
    }
    public Object nativeFilterProcessor(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: nativeFilterProcessor");
    }
    public int setterCid(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: setterCid");
    }
    public Object setterProcessor(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: setterProcessor");
    }
    public Object processorPointer(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: processorPointer");
    }
    public Object processorQueue(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: processorQueue");
    }
    public Object providerKind(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: providerKind");
    }
    public Object flowFlag() {
        throw new IllegalStateException("Flow target adapter not patched: flowFlag");
    }
    public Object percentileFlag() {
        throw new IllegalStateException("Flow target adapter not patched: percentileFlag");
    }
    public Object alphaFlag() {
        throw new IllegalStateException("Flow target adapter not patched: alphaFlag");
    }
    public Object exclusionFlag() {
        throw new IllegalStateException("Flow target adapter not patched: exclusionFlag");
    }
    public Object spatialStats() {
        throw new IllegalStateException("Flow target adapter not patched: spatialStats");
    }
    public Object histogram(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: histogram");
    }
    public Object suppressionStatus() {
        throw new IllegalStateException("Flow target adapter not patched: suppressionStatus");
    }
    public Object successStatus() {
        throw new IllegalStateException("Flow target adapter not patched: successStatus");
    }
    public android.content.Context keyboardContext(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: keyboardContext");
    }
    public android.content.Context moduleContext(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: moduleContext");
    }
    public boolean eventAllowed(Object arg0, Object arg1) {
        throw new IllegalStateException("Flow target adapter not patched: eventAllowed");
    }
    public Object preferenceInstance(android.content.Context arg0) {
        throw new IllegalStateException("Flow target adapter not patched: preferenceInstance");
    }
    public boolean preferenceRead(Object receiver, String arg0, boolean arg1, boolean arg2) {
        throw new IllegalStateException("Flow target adapter not patched: preferenceRead");
    }
    public Object flagRead(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: flagRead");
    }
    public Object moduleInstance(android.content.Context arg0) {
        throw new IllegalStateException("Flow target adapter not patched: moduleInstance");
    }
    public Object moduleRead(Object receiver, Class<?> arg0) {
        throw new IllegalStateException("Flow target adapter not patched: moduleRead");
    }
    public Object cacheRead(Object receiver, Object arg0, Class<?> arg1) {
        throw new IllegalStateException("Flow target adapter not patched: cacheRead");
    }
    public void cancel(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: cancel");
    }
    public void suppressed(Object receiver, Object arg0, Object arg1, Object arg2) {
        throw new IllegalStateException("Flow target adapter not patched: suppressed");
    }
    public Object moduleStatus(Object receiver) {
        throw new IllegalStateException("Flow target adapter not patched: moduleStatus");
    }
    public Object infer(Object receiver, Object arg0) {
        throw new IllegalStateException("Flow target adapter not patched: infer");
    }
    public Object immediate(Object arg0) {
        throw new IllegalStateException("Flow target adapter not patched: immediate");
    }
    public Object queue(Object receiver, java.util.concurrent.Callable<?> arg0) {
        throw new IllegalStateException("Flow target adapter not patched: queue");
    }
    public void pointerRead(Object receiver, Object arg0) {
        throw new IllegalStateException("Flow target adapter not patched: pointerRead");
    }
    public void nativeSet(Object receiver, long arg0, boolean arg1) {
        throw new IllegalStateException("Flow target adapter not patched: nativeSet");
    }
    public boolean isExtension(Object value) { return false; }
    public boolean isPayload(Object value) { return false; }
    public boolean isModule(Object value) { return false; }
    public boolean isProcessor(Object value) { return false; }
    public boolean isRequest(Object value) { return false; }
    public boolean isAction(Object value) { return false; }
    public boolean isStats(Object value) { return false; }
    public Class<?> cacheType() { throw new IllegalStateException("Unpatched Flow cache type"); }
    public Class<?> statsType() { throw new IllegalStateException("Unpatched Flow stats type"); }

    public boolean isEligible(Object extension) { return active(extension) && extensionCoordinator(extension)!=null; }
    public Integer firstKey(Object event) {
        Object value=actions(event);
        if(!(value instanceof Object[] list) || list.length==0 || list[0]==null
                || !isAction(list[0]) || !eventAllowed(event,list[0])) return null;
        return eventKeyCode(list[0]);
    }
    public boolean officialEnabled(android.content.Context context) {
        if(!Boolean.TRUE.equals(flagRead(flowFlag()))) return false;
        return preferenceRead(preferenceInstance(context),"enable_llm_pc_flow_mode",true,false);
    }
    public double transparency() { return readDouble(alphaFlag(),.5); }
    public double exclusion() { return readDouble(exclusionFlag(),.33); }
    private double readDouble(Object flag,double fallback) {
        Object value=flagRead(flag);
        return value instanceof Double && Double.isFinite((Double)value)?(Double)value:fallback;
    }
    public long threshold(android.content.Context context) {
        try {
            Object scalar=flagRead(percentileFlag());
            int percentile=scalar instanceof Long && (Long)scalar>=0 && (Long)scalar<=100
                    ?((Long)scalar).intValue():30;
            Object cache=moduleRead(moduleInstance(context),cacheType());
            if(cache==null) return GboardFlowModeDetector.FALLBACK_NANOS;
            Object stats=cacheRead(cache,spatialStats(),statsType());
            if(!isStats(stats)) return GboardFlowModeDetector.FALLBACK_NANOS;
            Object value=histogram(stats);
            return value instanceof java.util.Map<?,?>
                    ?GboardFlowModeDetector.thresholdFromHistogram((java.util.Map<?,?>)value,percentile)
                    :GboardFlowModeDetector.FALLBACK_NANOS;
        } catch(Throwable unavailable) { return GboardFlowModeDetector.FALLBACK_NANOS; }
    }
}
