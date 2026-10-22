package dev.jason.gboardpatches.extension.flowmode;

import android.view.inputmethod.EditorInfo;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class GboardFlowModeRequestBridge {
    private volatile boolean healthy=true;
    private final java.util.concurrent.atomic.AtomicInteger nativeFailures=new java.util.concurrent.atomic.AtomicInteger();
    private final GboardFlowModeAccess access;
    private final IdentityWeakMap<Snapshot> requests=new IdentityWeakMap<>(),tasks=new IdentityWeakMap<>();
    private final IdentityWeakMap<ReentrantLock> processorLocks=new IdentityWeakMap<>();
    private final ThreadLocal<ArrayDeque<Scope>> eventScopes=ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<ArrayDeque<Scope>> continuationScopes=ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<ArrayDeque<Scope>> taskScopes=ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<ArrayDeque<NativeScope>> nativeScopes=ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<ArrayDeque<NativeScope>> setterScopes=ThreadLocal.withInitial(ArrayDeque::new);

    public GboardFlowModeRequestBridge(GboardFlowModeAccess access) {
        this.access=java.util.Objects.requireNonNull(access);
    }

    public boolean enterEvent(Object extension,Object event,boolean enabled) throws Throwable {
        eventScopes.get().push(new Scope(null,null));
        Snapshot snapshot=null;
        if(enabled && healthy && access.isExtension(extension)) {
            Object token=GboardFlowModeRuntime.stateToken(extension);
            Object editor=access.extensionEditor(extension);
            if(token!=null && editor instanceof EditorInfo) snapshot=new Snapshot(extension,token,editor,
                    GboardFlowModeRuntime.isFlowingFor(extension));
        }
        eventScopes.get().pop();eventScopes.get().push(new Scope(snapshot,null));
        if(snapshot==null || !snapshot.valid(this) || !snapshot.flow) return false;
        Object raw=access.actions(event);
        if(!(raw instanceof Object[] list) || list.length==0 || !access.isAction(list[0])) return false;
        if(access.eventKeyCode(list[0])!=-700004) return false;
        Object request=access.payload(list[0]);
        if(!access.isPayload(request)) return false;
        Object kind=access.requestType(request);
        if(!(kind instanceof Enum<?>)) return false;
        String name=((Enum<?>)kind).name();
        if(!name.equals("END_OF_NEXT_WORD_PREDICTION")
                && !name.equals("DELAYED_END_OF_NEXT_WORD_PREDICTION")
                && !name.equals("END_OF_GESTURE") && !name.equals("END_OF_TAP")) return false;
        Object coordinator=access.extensionCoordinator(extension);
        if(coordinator==null) return false;
        access.cancel(coordinator);access.suppressed(coordinator,request,null,access.suppressionStatus());
        return true;
    }
    public void exitEvent(){pop(eventScopes);}

    public boolean resetModule(Object module) throws Throwable {
        Snapshot snapshot=current(eventScopes);
        if(!healthy || snapshot==null || !snapshot.valid(this) || !access.isModule(module)) return false;
        AtomicBoolean state=(AtomicBoolean)access.javaState(module);
        Object processor=access.currentProcessor(module);
        if(processor!=null && !access.isProcessor(processor)) return false;
        if(processor!=null) scheduleNative(processor,snapshot.forModule(module).forProcessor(processor));
        state.set(false);
        return true;
    }
    public void tagRequest(Object module,Object request) {
        Snapshot snapshot=current(eventScopes);
        if(snapshot!=null && access.isModule(module) && access.isRequest(request)
                && snapshot.valid(this)) requests.put(request,snapshot.forModule(module));
    }
    public Object bypassPreFilter(Object continuation,Object status) throws Throwable {
        if(access.prefilterCid(continuation)!=0) return null;
        Object module=access.prefilterModule(continuation),request=access.prefilterRequest(continuation);
        Snapshot snapshot=requests.get(request);
        if(!healthy || snapshot==null || !snapshot.flow || !snapshot.validModule(this,module)) return null;
        if(status!=access.successStatus() && access.moduleStatus(module)!=access.successStatus()) return null;
        return access.immediate(access.infer(module,request));
    }
    public void enterContinuation(Object continuation) throws Throwable {
        continuationScopes.get().push(new Scope(null,null));
        Snapshot snapshot=null;Object processor=null;
        if(access.continuationCid(continuation)==10) {
            Object request=access.continuationRequest(continuation),module=access.continuationModule(continuation);
            Object candidate=access.continuationProcessor(continuation);
            Snapshot found=requests.get(request);
            if(found!=null && found.validModule(this,module) && access.isProcessor(candidate)) {
                snapshot=found;processor=candidate;
            }
        }
        continuationScopes.get().pop();continuationScopes.get().push(new Scope(snapshot,processor));
    }
    public void exitContinuation(){pop(continuationScopes);}
    public void tagPostprocessTask(Object task,Object processor) {
        Scope scope=continuationScopes.get().peek();
        if(scope!=null && scope.snapshot!=null && scope.processor==processor
                && scope.snapshot.valid(this)) tasks.put(task,scope.snapshot.forProcessor(processor));
    }
    public void enterPostprocess(Object task) throws Throwable {
        taskScopes.get().push(new Scope(null,null));
        Object processor=access.taskProcessor(task);Snapshot snapshot=tasks.get(task);
        if(snapshot==null || !snapshot.validProcessor(this,processor)) snapshot=null;
        taskScopes.get().pop();taskScopes.get().push(new Scope(snapshot,processor));
    }
    public void exitPostprocess(){pop(taskScopes);}
    public boolean excludeIntendedOov(Object provider) throws Throwable {
        Snapshot snapshot=current(taskScopes);
        if(snapshot==null || !snapshot.flow || !snapshot.valid(this)) return false;
        Object kind=access.providerKind(provider);
        return kind instanceof Enum<?> && ((Enum<?>)kind).name().equals("INTENDED_OOV_FILTER");
    }
    public boolean skipIntendedOovLookup() {
        Snapshot snapshot=current(taskScopes);
        return snapshot!=null && snapshot.flow && snapshot.valid(this);
    }

    public void enterNativeResponse(Object callback,long handle) throws Throwable {
        nativeScopes.get().push(NativeScope.EMPTY);
        Scope scope=taskScopes.get().peek();Snapshot snapshot=scope==null?null:scope.snapshot;
        int discriminator=access.nativeResponseCid(callback);
        Object processor=(discriminator==0?access.nativeFilterProcessor(callback):access.nativeResponseProcessor(callback));
        NativeScope locked=null;
        if(handle!=0 && access.isProcessor(processor)) {
            ReentrantLock lock=lock(processor);lock.lock();
            try {
                boolean flow=discriminator!=0 && snapshot!=null && scope.processor==processor
                        && snapshot.validProcessor(this,processor) && snapshot.flow;
                access.nativeSet(processor,handle,flow);
                locked=new NativeScope(processor,handle,lock,flow?snapshot:null);
            } catch(Throwable failure) {
                try { access.nativeSet(processor,handle,false); }
                catch(Throwable rollback) { failure.addSuppressed(rollback); }
                finally { lock.unlock(); }
                nativeFailure(failure);throw failure;
            }
        }
        nativeScopes.get().pop();nativeScopes.get().push(locked==null?NativeScope.EMPTY:locked);
    }
    public void exitNativeResponse() {
        NativeScope scope=pop(nativeScopes);
        if(scope==null || scope==NativeScope.EMPTY) return;
        NativeScope parent=nativeScopes.get().peek();
        boolean restore=healthy && parent!=null && parent.processor==scope.processor
                && parent.handle==scope.handle && parent.snapshot!=null
                && parent.snapshot.validProcessor(this,scope.processor) && parent.snapshot.flow;
        try { access.nativeSet(scope.processor,scope.handle,restore); }
        catch(Throwable failure) { nativeFailure(failure); }
        finally { scope.lock.unlock(); }
    }
    public void enterStockSetter(Object callback) throws Throwable {
        setterScopes.get().push(NativeScope.EMPTY);
        NativeScope scope=NativeScope.EMPTY;
        if(access.setterCid(callback)>1) {
            Object processor=access.setterProcessor(callback);ReentrantLock lock=lock(processor);lock.lock();
            scope=new NativeScope(processor,0,lock);
        }
        setterScopes.get().pop();setterScopes.get().push(scope);
    }
    public void exitStockSetter() {
        NativeScope scope=pop(setterScopes);if(scope!=null && scope!=NativeScope.EMPTY) scope.lock.unlock();
    }
    private void scheduleNative(Object processor,Snapshot snapshot) throws Throwable {
        Object executor=access.processorQueue(processor),owner=access.processorPointer(processor);
        Callable<Object> work=()->{
            access.pointerRead(owner,new GboardFlowModePointerCallback(this,processor,snapshot));
            return null;
        };
        try {
            if(access.queue(executor,work)==null) throw new IllegalStateException("Flow scheduler returned null");
        } catch(Throwable failure) { nativeFailure(failure);throw failure; }
    }
    void scheduledNative(Object processor,Object rawSnapshot,long handle) {
        if(handle==0 || !(rawSnapshot instanceof Snapshot snapshot)) return;
        ReentrantLock lock=lock(processor);lock.lock();
        try { access.nativeSet(processor,handle,snapshot.validProcessor(this,processor) && snapshot.flow); }
        catch(Throwable failure) {
            try { access.nativeSet(processor,handle,false); }
            catch(Throwable rollback) { failure.addSuppressed(rollback); }
            nativeFailure(failure);
        } finally { lock.unlock(); }
    }
    public boolean isHealthy() { return healthy; }
    private void nativeFailure(Throwable failure) {
        healthy=false;
        if(nativeFailures.getAndIncrement()<3) {
            android.util.Log.w("GboardPatches","Flow native state cleanup failed; effects disabled",failure);
        }
    }
    private ReentrantLock lock(Object processor) {
        synchronized(processorLocks) {
            ReentrantLock lock=processorLocks.get(processor);
            if(lock==null) {lock=new ReentrantLock();processorLocks.put(processor,lock);}return lock;
        }
    }
    private static Snapshot current(ThreadLocal<ArrayDeque<Scope>> scopes) {
        Scope scope=scopes.get().peek();return scope==null?null:scope.snapshot;
    }
    private static <T> T pop(ThreadLocal<ArrayDeque<T>> scopes) {
        ArrayDeque<T> stack=scopes.get();T value=stack.poll();if(stack.isEmpty())scopes.remove();return value;
    }
    private static final class Scope {
        final Snapshot snapshot;final Object processor;
        Scope(Snapshot snapshot,Object processor){this.snapshot=snapshot;this.processor=processor;}
    }
    private static final class NativeScope {
        static final NativeScope EMPTY=new NativeScope(null,0,null,null);
        final Object processor;final long handle;final ReentrantLock lock;final Snapshot snapshot;
        NativeScope(Object processor,long handle,ReentrantLock lock){this(processor,handle,lock,null);}
        NativeScope(Object processor,long handle,ReentrantLock lock,Snapshot snapshot){
            this.processor=processor;this.handle=handle;this.lock=lock;this.snapshot=snapshot;
        }
    }
    private static final class Snapshot {
        final WeakReference<Object> extension,editor,module,processor;final Object token;final boolean flow;
        Snapshot(Object extension,Object token,Object editor,boolean flow) {
            this(extension,token,editor,flow,null,null);
        }
        Snapshot(Object extension,Object token,Object editor,boolean flow,Object module,Object processor) {
            this.extension=new WeakReference<>(extension);this.editor=new WeakReference<>(editor);
            this.module=new WeakReference<>(module);this.processor=new WeakReference<>(processor);
            this.token=token;this.flow=flow;
        }
        Snapshot forModule(Object module){return new Snapshot(extension.get(),token,editor.get(),flow,module,null);}
        Snapshot forProcessor(Object processor){return new Snapshot(extension.get(),token,editor.get(),flow,module.get(),processor);}
        boolean valid(GboardFlowModeRequestBridge binding) {
            Object extension=this.extension.get(),editor=this.editor.get();
            if(!binding.healthy || extension==null || editor==null || !GboardFlowModeRuntime.isCurrent(extension,token))return false;
            try { return binding.access.extensionEditor(extension)==editor; }
            catch(Throwable failure) {return false;}
        }
        boolean validModule(GboardFlowModeRequestBridge binding,Object module) {
            return module!=null && binding.access.isModule(module) && this.module.get()==module && valid(binding);
        }
        boolean validProcessor(GboardFlowModeRequestBridge binding,Object processor) {
            Object module=this.module.get();
            if(processor==null || !binding.access.isProcessor(processor)
                    || this.processor.get()!=processor || !validModule(binding,module)) return false;
            try { return binding.access.currentProcessor(module)==processor; }
            catch(Throwable failure) { return false; }
        }
    }
    static final class IdentityWeakMap<V> {
        private final ReferenceQueue<Object> queue=new ReferenceQueue<>();
        private final Map<Key,V> map=new HashMap<>();
        synchronized void put(Object key,V value){drain();map.put(new Key(key,queue),value);}
        synchronized V get(Object key){drain();return key==null?null:map.get(new Key(key,null));}
        private void drain(){Key key;while((key=(Key)queue.poll())!=null)map.remove(key);}
        private static final class Key extends WeakReference<Object> {
            final int hash;Key(Object value,ReferenceQueue<Object> queue){super(value,queue);hash=System.identityHashCode(value);}
            public int hashCode(){return hash;}
            public boolean equals(Object other){return this==other || other instanceof Key
                    && get()!=null && get()==((Key)other).get();}
        }
    }
}
