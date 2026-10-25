package org.privateprice.pricebook;

import android.os.Debug;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Local display only. Sampled process PSS is not an exact per-engine/native peak. */
final class OcrMemory implements AutoCloseable {
    private final AtomicBoolean running=new AtomicBoolean(true);
    private final AtomicLong maxKb=new AtomicLong();
    private final Thread sampler;
    OcrMemory(){
        sampler=new Thread(()->{while(running.get()){
            try{long value=Debug.getPss();maxKb.accumulateAndGet(value,Math::max);Thread.sleep(200);}
            catch(InterruptedException e){return;}catch(RuntimeException e){return;}
        }},"local-ocr-memory-sample");sampler.start();
    }
    long maximumKb(){return maxKb.get();}
    public void close(){running.set(false);sampler.interrupt();try{sampler.join(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
