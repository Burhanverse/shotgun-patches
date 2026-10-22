package dev.jason.gboardpatches.extension.flowmode;

import android.view.inputmethod.EditorInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,manifest=Config.NONE)
public final class GboardFlowModeRequestBridgeTest {
    @After public void clear() throws Exception {
        Field field=GboardFlowModeRuntime.class.getDeclaredField("STATES");field.setAccessible(true);
        ((Map<?,?>)field.get(null)).clear();
    }
    @Test public void onlyExactFourBoundaryTypesSuppressAndStockOffNeverBorrowsScope() throws Throwable {
        Fixture f=new Fixture(true);
        for(Kind kind:new Kind[]{Kind.END_OF_NEXT_WORD_PREDICTION,Kind.DELAYED_END_OF_NEXT_WORD_PREDICTION,
                Kind.END_OF_GESTURE,Kind.END_OF_TAP}) {
            Assert.assertTrue(f.bridge.enterEvent(f.extension,new Event(-700004,new Payload(kind)),true));
            f.bridge.exitEvent();
        }
        Assert.assertEquals(4,f.extension.i.cancelled);Assert.assertEquals(4,f.extension.i.suppressed);
        Assert.assertFalse(f.bridge.enterEvent(f.extension,new Event(12,new Payload(Kind.END_OF_TAP)),true));
        f.bridge.exitEvent();
        Assert.assertFalse(f.bridge.enterEvent(f.extension,new Event(-700004,new Payload(Kind.END_OF_SENTENCE_TERMINATOR)),true));
        f.bridge.exitEvent();
        Assert.assertFalse(f.bridge.enterEvent(f.extension,new Event(-700004,new Payload(Kind.END_OF_TAP)),false));
        Assert.assertFalse(f.bridge.resetModule(f.module));f.bridge.exitEvent();
        Assert.assertEquals(4,f.extension.i.suppressed);
    }
    @Test public void capturedTrueAndStockFalseRequestsNeverShareTheMutableJavaBoolean() throws Throwable {
        Fixture f=new Fixture(true);Request active=f.tag();
        Assert.assertFalse("Stock Java state must not leak true into another async request",f.module.g.get());
        Assert.assertNotNull(f.bridge.bypassPreFilter(new PreFilter(f.module,active),Status.a));
        Assert.assertEquals(1,f.module.inferences);
        Assert.assertNull(f.bridge.bypassPreFilter(new PreFilter(f.module,new Request()),Status.a));
        Assert.assertEquals(1,f.module.inferences);
        f.setFlow(false);Request ordinary=f.tag();
        Assert.assertNull(f.bridge.bypassPreFilter(new PreFilter(f.module,ordinary),Status.a));
        Assert.assertNotNull("Earlier true request retains its own immutable snapshot",
                f.bridge.bypassPreFilter(new PreFilter(f.module,active),Status.a));
    }
    @Test public void staleGenerationEditorAndProcessorPreventAsyncEffects() throws Throwable {
        Fixture f=new Fixture(true);Request request=f.tag();
        f.extension.a=new EditorInfo();
        Assert.assertNull(f.bridge.bypassPreFilter(new PreFilter(f.module,request),Status.a));
        Fixture g=new Fixture(true);Request active=g.tag();Task task=g.task(active);
        PP old=g.processor;g.module.n=new PP();
        g.bridge.enterPostprocess(task);
        Assert.assertFalse(g.bridge.skipIntendedOovLookup());g.bridge.exitPostprocess();
        g.module.n=old;GboardFlowModeRuntime.reset(g.extension);
        Assert.assertNull(g.bridge.bypassPreFilter(new PreFilter(g.module,active),Status.a));
    }
    @Test public void personalizationExcludesBothOovPathsOnlyInsideTheTaggedWorker() throws Throwable {
        Fixture f=new Fixture(true);Task task=f.task(f.tag());
        Assert.assertFalse(f.bridge.skipIntendedOovLookup());f.bridge.enterPostprocess(task);
        try {
            Assert.assertTrue(f.bridge.skipIntendedOovLookup());
            Assert.assertTrue(f.bridge.excludeIntendedOov(new Provider(Filter.INTENDED_OOV_FILTER)));
            for(Filter kind:new Filter[]{Filter.CONTACT_FILTER,Filter.PERSONAL_WORD_FILTER,Filter.RECENT_EMOTICON_FILTER})
                Assert.assertFalse(f.bridge.excludeIntendedOov(new Provider(kind)));
        } finally { f.bridge.exitPostprocess(); }
        Assert.assertFalse(f.bridge.skipIntendedOovLookup());
        f.bridge.enterPostprocess(new Task(f.processor,new Response()));
        Assert.assertFalse(f.bridge.skipIntendedOovLookup());f.bridge.exitPostprocess();
    }
    @Test public void closedZeroHandleAndReplacementCannotInvokeTheSetter() throws Throwable {
        Fixture f=new Fixture(true);f.tag();f.processor.d.handle=0;f.processor.j.runAll();
        Assert.assertEquals(0,f.processor.setCalls);
        Fixture g=new Fixture(true);g.tag();g.module.n=new PP();g.processor.j.runAll();
        Assert.assertFalse(g.processor.flow);Assert.assertEquals(List.of(false),g.processor.states);
    }
    @Test public void concurrentTaggedAndUntaggedNativeCallsAreSerializedAndRestoreFalse() throws Throwable {
        Fixture f=new Fixture(true);Task task=f.task(f.tag());
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),stockEntered=new CountDownLatch(1);
        List<Throwable> failures=java.util.Collections.synchronizedList(new ArrayList<>());
        Thread active=new Thread(()->{
            try {
                f.bridge.enterPostprocess(task);
                f.processor.d.c(pointer->{
                    try {
                        f.bridge.enterNativeResponse(new NativeCallback(f.processor,1),pointer);
                        Assert.assertTrue(f.processor.flow);entered.countDown();
                        Assert.assertTrue(release.await(5,TimeUnit.SECONDS));
                    } catch(Throwable failure){throw new RuntimeException(failure);}
                    finally{f.bridge.exitNativeResponse();}
                });
            } catch(Throwable failure){failures.add(failure);}
            finally{f.bridge.exitPostprocess();}
        });
        Thread stock=new Thread(()->{
            try {
                Assert.assertTrue(entered.await(5,TimeUnit.SECONDS));
                f.processor.d.c(pointer->{
                    try {
                        f.bridge.enterNativeResponse(new NativeCallback(f.processor,1),pointer);
                        Assert.assertFalse(f.processor.flow);stockEntered.countDown();
                    } catch(Throwable failure){throw new RuntimeException(failure);}
                    finally{f.bridge.exitNativeResponse();}
                });
            } catch(Throwable failure){failures.add(failure);}
        });
        active.start();stock.start();Assert.assertTrue(entered.await(5,TimeUnit.SECONDS));
        Assert.assertFalse(stockEntered.await(100,TimeUnit.MILLISECONDS));release.countDown();
        active.join(5000);stock.join(5000);
        Assert.assertFalse(active.isAlive());Assert.assertFalse(stock.isAlive());
        Assert.assertTrue(failures.toString(),failures.isEmpty());Assert.assertFalse(f.processor.flow);
    }
    @Test public void partialSetterFailureRollsBackAndMarksTheBridgeUnhealthyWithoutLeakingLock() throws Throwable {
        Fixture f=new Fixture(true);Task task=f.task(f.tag());f.processor.failTrue=true;
        f.bridge.enterPostprocess(task);
        try {
            f.processor.d.c(pointer->{
                try {
                    f.bridge.enterNativeResponse(new NativeCallback(f.processor,1),pointer);
                    Assert.fail("setter must fail");
                } catch(Throwable expected) { Assert.assertFalse(f.processor.flow); }
                finally { f.bridge.exitNativeResponse(); }
            });
        } finally { f.bridge.exitPostprocess(); }
        Assert.assertFalse(f.bridge.isHealthy());f.processor.failTrue=false;
        f.processor.d.c(pointer->{
            try { f.bridge.enterNativeResponse(new NativeCallback(f.processor,1),pointer); }
            catch(Throwable failure){throw new RuntimeException(failure);}
            finally {f.bridge.exitNativeResponse();}
        });
        Assert.assertFalse(f.processor.flow);
    }

    @Test public void nestedStockNativeWindowRestoresTheOuterCapturedTrueBeforeReturning() throws Throwable {
        Fixture f=new Fixture(true);Task active=f.task(f.tag());f.bridge.enterPostprocess(active);
        try {
            f.processor.d.c(pointer->{
                try {
                    f.bridge.enterNativeResponse(new NativeCallback(f.processor,1),pointer);
                    Assert.assertTrue(f.processor.flow);
                    f.bridge.enterPostprocess(new Task(f.processor,new Response()));
                    try {
                        f.bridge.enterNativeResponse(new NativeCallback(f.processor,0),pointer);
                        Assert.assertFalse(f.processor.flow);
                        f.bridge.exitNativeResponse();
                        Assert.assertTrue("Nested stock cleanup must restore its active parent",f.processor.flow);
                    } finally { f.bridge.exitPostprocess(); }
                } catch(Throwable failure) {throw new RuntimeException(failure);}
                finally {f.bridge.exitNativeResponse();}
            });
        } finally {f.bridge.exitPostprocess();}
        Assert.assertFalse(f.processor.flow);
    }
    @Test public void queueRejectionAndMissingShapesCannotReportReadyOrBorrowAnOldSnapshot() throws Throwable {
        Fixture f=new Fixture(true);f.processor.j.reject=true;
        try {f.tag();Assert.fail("Rejected queue must fail this effect");}
        catch(java.util.concurrent.RejectedExecutionException expected) {Assert.assertFalse(f.bridge.isHealthy());}
        Assert.assertEquals(0,f.processor.setCalls);
    }

    private static final class Fixture {
        final Extension extension=new Extension();final Module module=new Module();final PP processor=new PP();
        final GboardFlowModeRequestBridge bridge;
        Object state;
        Fixture(boolean flow)throws Throwable {
            module.n=processor;bridge=new GboardFlowModeRequestBridge(new Access());
            Class<?> stateType=Class.forName(GboardFlowModeRuntime.class.getName()+"$State");
            var constructor=stateType.getDeclaredConstructor();constructor.setAccessible(true);state=constructor.newInstance();
            Field states=GboardFlowModeRuntime.class.getDeclaredField("STATES");states.setAccessible(true);
            @SuppressWarnings("unchecked") Map<Object,Object> map=(Map<Object,Object>)states.get(null);map.put(extension,state);
            setFlow(flow);
        }
        void setFlow(boolean flow)throws Exception {
            Field detectorField=state.getClass().getDeclaredField("detector");detectorField.setAccessible(true);
            var detector=(GboardFlowModeDetector)detectorField.get(state);detector.reset();
            if(flow) for(int i=0;i<=15;i++)detector.onKey(-10043,1+i*10_000_000L);
        }
        Request tag()throws Throwable {
            Request request=new Request();bridge.enterEvent(extension,new Event(1,null),true);
            try { Assert.assertTrue(bridge.resetModule(module));bridge.tagRequest(module,request); }
            finally {bridge.exitEvent();}return request;
        }
        Task task(Request request)throws Throwable {
            bridge.enterContinuation(new Continuation(module,request,processor));
            try {Task task=new Task(processor,new Response());bridge.tagPostprocessTask(task,processor);return task;}
            finally{bridge.exitContinuation();}
        }
    }
    private static final class Access extends GboardFlowModeAccess {
        @Override public boolean isExtension(Object value){return value instanceof Extension;}
        @Override public boolean isPayload(Object value){return value instanceof Payload;}
        @Override public boolean isModule(Object value){return value instanceof Module;}
        @Override public boolean isProcessor(Object value){return value instanceof PP;}
        @Override public boolean isRequest(Object value){return value instanceof Request;}
        @Override public boolean isAction(Object value){return value instanceof Action;}
        @Override public EditorInfo extensionEditor(Object value){return ((Extension)value).a;}
        @Override public Object extensionCoordinator(Object value){return ((Extension)value).i;}
        @Override public Object actions(Object value){return ((Event)value).b;}
        @Override public Object payload(Object value){return ((Action)value).e;}
        @Override public int eventKeyCode(Object value){return ((Action)value).c;}
        @Override public Object requestType(Object value){return ((Payload)value).a;}
        @Override public void cancel(Object value){((Coordinator)value).e();}
        @Override public void suppressed(Object value,Object request,Object result,Object status){((Coordinator)value).f((Payload)request,result,(Suppression)status);}
        @Override public Object suppressionStatus(){return Suppression.c;}
        @Override public Object successStatus(){return Status.a;}
        @Override public AtomicBoolean javaState(Object value){return ((Module)value).g;}
        @Override public Object currentProcessor(Object value){return ((Module)value).n;}
        @Override public Object moduleStatus(Object value){return ((Module)value).n();}
        @Override public Object infer(Object value,Object request){return ((Module)value).p((Request)request);}
        @Override public Object immediate(Object value){return Futures.q(value);}
        @Override public int prefilterCid(Object value){return ((PreFilter)value).$cid;}
        @Override public Object prefilterModule(Object value){return ((PreFilter)value).b;}
        @Override public Object prefilterRequest(Object value){return ((PreFilter)value).c;}
        @Override public int continuationCid(Object value){return ((Continuation)value).$cid;}
        @Override public Object continuationModule(Object value){return ((Continuation)value).a;}
        @Override public Object continuationRequest(Object value){return ((Continuation)value).b;}
        @Override public Object continuationProcessor(Object value){return ((Continuation)value).c;}
        @Override public Object taskProcessor(Object value){return ((Task)value).a;}
        @Override public Object providerKind(Object value){return ((Provider)value).a;}
        @Override public int nativeResponseCid(Object value){return ((NativeCallback)value).$cid;}
        @Override public Object nativeFilterProcessor(Object value){return ((NativeCallback)value).a;}
        @Override public Object nativeResponseProcessor(Object value){return ((NativeCallback)value).b;}
        @Override public int setterCid(Object value){return ((StockSetter)value).$cid;}
        @Override public Object setterProcessor(Object value){return ((StockSetter)value).a;}
        @Override public Object processorQueue(Object value){return ((PP)value).j;}
        @Override public Object processorPointer(Object value){return ((PP)value).d;}
        @Override public Object queue(Object value,Callable<?> work){return ((Queue)value).hL(work);}
        @Override public void pointerRead(Object value,Object callback){((PointerOwner)value).c(((GboardFlowModePointerCallback)callback)::invoke);}
        @Override public void nativeSet(Object value,long pointer,boolean flow){((PP)value).postProcessorSetFlowModeNative(pointer,flow);}
    }
    public static final class Extension {public EditorInfo a=new EditorInfo();public Coordinator i=new Coordinator();}
    public static final class Coordinator {
        int cancelled,suppressed;public void e(){cancelled++;}public void f(Payload p,Object result,Suppression s){suppressed++;}
    }
    public static final class Action {public final int c;public final Object e;Action(int c,Object e){this.c=c;this.e=e;}}
    public static final class Event {public Action[] b;Event(int code,Object payload){b=new Action[]{new Action(code,payload)};}}
    public static final class Payload {public Kind a;Payload(Kind a){this.a=a;}}
    public enum Kind {END_OF_NEXT_WORD_PREDICTION,DELAYED_END_OF_NEXT_WORD_PREDICTION,END_OF_GESTURE,END_OF_TAP,END_OF_SENTENCE_TERMINATOR}
    public enum Status {a,b} public enum Suppression {c}
    public static final class Request {} public static final class Response {}
    public interface Iug {}
    public static final class Module {
        public final AtomicBoolean g=new AtomicBoolean();private volatile Iug n;int inferences;
        public void k(){}public Future h(Request request){return new Future(null);}
        public Status n(){return Status.a;}public Response p(Request request){inferences++;return new Response();}
    }
    public static final class Future {final Object result;Future(Object result){this.result=result;}}
    public static final class Futures {public static Future q(Object result){return new Future(result);}}
    public static final class PreFilter {
        private final int $cid=0;public final Object b,c;
        PreFilter(Module module,Request request){b=module;c=request;}public Future a(Object status){return null;}
    }
    public static final class Continuation {
        private final int $cid=10;public final Object a,b,c;
        Continuation(Module module,Request request,PP pp){a=module;b=request;c=pp;}public Future a(Object result){return null;}
    }
    public static final class Task {
        public final PP a;public final Response b;public Task(PP pp,Response response){a=pp;b=response;}
        public Object call(){return null;}
    }
    public static final class NativeCallback {
        private final int $cid;public final Object a,b;
        NativeCallback(PP pp,int cid){$cid=cid;a=pp;b=pp;}public Object a(long pointer){return null;}
    }
    public static final class StockSetter {
        private final int $cid=2;public final PP a;StockSetter(PP pp){a=pp;}public void invoke(long pointer){}
    }
    public interface PointerCallback {void invoke(long pointer);}
    public static final class PointerOwner {
        final ReentrantReadWriteLock lock=new ReentrantReadWriteLock();volatile long handle=17;
        public void c(PointerCallback callback){lock.readLock().lock();try{if(handle!=0)callback.invoke(handle);}finally{lock.readLock().unlock();}}
    }
    public static final class Queue {
        boolean reject;final List<Callable<?>> pending=new ArrayList<>();public Future hL(Callable<?> task){
            if(reject)throw new java.util.concurrent.RejectedExecutionException();pending.add(task);return new Future(null);
        }
        void runAll()throws Exception {for(Callable<?> task:pending)task.call();pending.clear();}
    }
    public static final class PP implements Iug {
        public final PointerOwner d=new PointerOwner();private final Queue j=new Queue();
        boolean flow,failTrue;int setCalls;final List<Boolean> states=new ArrayList<>();
        public void postProcessorSetFlowModeNative(long pointer,boolean enabled){
            Assert.assertTrue("Handle must remain protected by stock owner read lock",d.lock.getReadHoldCount()>0);
            setCalls++;flow=enabled;states.add(enabled);if(enabled&&failTrue)throw new IllegalStateException("partial native write");
        }
    }
    public enum Filter {CONTACT_FILTER,PERSONAL_WORD_FILTER,RECENT_EMOTICON_FILTER,INTENDED_OOV_FILTER}
    public static final class Provider {public final Filter a;Provider(Filter a){this.a=a;}public Object a(){return new Object();}}
    public static final class Delight {public static Delight f(){return new Delight();}}
}
