package io.gleecy.db;

import org.apache.commons.lang3.StringUtils;
import org.moqui.Moqui;
import org.moqui.context.*;
import org.moqui.entity.EntityValue;
import org.moqui.impl.context.*;
import org.moqui.impl.entity.EntityDefinition;
import org.moqui.impl.entity.EntityJavaUtil;
import org.moqui.impl.entity.EntityValueBase;
import org.moqui.util.ObjectUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DBWorker {
    private static final String SUCCESS_MESSAGE = "Imported successfully.";

    private static final class Lock {}
    private static final Logger LOGGER = LoggerFactory.getLogger(DBWorker.class);
    static class CrURunable implements Runnable {
        private final Lock lock = new Lock();
        private final int maxThreads;
        private final int numTasksPerThread;

        final Queue<AbstractMap.SimpleEntry<List<String>, EntityValue>> taskQueue = new LinkedList<>();
        final List<List<String>> results = new ArrayList<>();
        final AtomicInteger numThreads = new AtomicInteger(0);
        final AtomicBoolean keepAlive = new AtomicBoolean(true);
        final String username;
        ExecutionContextFactoryImpl ecfi;

        CrURunable(ExecutionContext ec, int maxThreads, int numTasksPerThread) {
            this.username = ec.getUser().getUsername();
            this.maxThreads = maxThreads;
            this.numTasksPerThread = numTasksPerThread;
            this.ecfi = (ExecutionContextFactoryImpl) ec.getFactory();
        }
        boolean _wait(String waitingFor) {
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    LOGGER.error(Thread.currentThread().getName()
                            + ": Got InterruptedException while "
                            + waitingFor, e);
                    return true;
                }
            }
            return false;
        }
        void _notify() {
            synchronized (lock) {
                lock.notify();
            }
        }
        void _notifyAll() {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
        void addResult(List<String> result) {
            synchronized (this.results) {
                this.results.add(result);
            }
        }
         AbstractMap.SimpleEntry<List<String>, EntityValue> pollTask() {
            synchronized (this.taskQueue) {
                return this.taskQueue.poll();
            }
        }
        void queueTask(List<String> rowValues, EntityValue task) {
            //LOGGER.info("queue task");
            int numExpectedThreads ;
            synchronized (this.taskQueue) {
                this.taskQueue.offer(new AbstractMap.SimpleEntry<>(rowValues, task));
                int numTasks = this.taskQueue.size();
                if(numTasks == 0) {
                    return;
                }
                numExpectedThreads = numTasks/numTasksPerThread + 1;
                if(numExpectedThreads > maxThreads)
                    numExpectedThreads = maxThreads;
            }

            synchronized (this.numThreads) {
                //LOGGER.info("Checking num thread");
                int iNumThreads =  this.numThreads.get();
                if(iNumThreads < numExpectedThreads) {
                    //LOGGER.info("Creating new thread");
                    //String newThreadName = this.workerGroup.getName()
                    //        + "_" + this.numThreads.addAndGet(1);
                    ecfi.workerPool.execute(this);
                    this.numThreads.addAndGet(1);
                    //new Thread(this.workerGroup, this, newThreadName).start();
                    return;
                }
            }

            //LOGGER.info("Notifying other threads");
            this._notify();
        }
        public void shutdown() {
            this.keepAlive.set(false);
            _notifyAll();
            while (this.numThreads.get() > 0) {
                if(_wait("waiting for other threads to shutdown"))
                    break;
            }
        }

        ExecutionContextFactoryImpl getEcfi() {
            if (ecfi == null)
                ecfi = (ExecutionContextFactoryImpl) Moqui.getExecutionContextFactory();
            return ecfi;
        }
        transient Map<String, Set<String>> fkFMap = new HashMap<>();
        private Set<String> getFkFieldNames(EntityDefinition ed) {
            Set<String> fks = fkFMap.get(ed.fullEntityName);
            if(fks != null) return fks;
            fks = new HashSet<>();
            ArrayList<EntityJavaUtil.RelationshipInfo> relInfos = ed.getRelationshipsInfo(false);
            for(EntityJavaUtil.RelationshipInfo relInfo : relInfos) {
                if(relInfo.isFk)
                    fks.addAll(relInfo.keyMap.keySet());
            }
            fkFMap.put(ed.fullEntityName, fks);
            LOGGER.debug("FK fields of " + ed.fullEntityName + ": " + StringUtils.join(fks, ","));
            return fks;
        }
        void store(List<String> rowValues, EntityValue entity, TransactionFacade tf) {
            EntityDefinition ed = ((EntityValueBase) entity).getEntityDefinition();
            Set<String> fks = getFkFieldNames(ed);
            Map<String, Object> primaryKeys = entity.getPrimaryKeys();
            int keyMissingCount = 0;
            for(Map.Entry<String, Object> entry : primaryKeys.entrySet()) {
                if (ObjectUtilities.isEmpty(entry.getValue())) {
                    if(fks.contains(entry.getKey())) {
                        rowValues.set(1, "Mandatory foreign key is missing: " + entry.getKey());
                        this.addResult(rowValues);
                        return;
                    }
                    keyMissingCount++;
                }
            }

            if (keyMissingCount > 2) {
                rowValues.set(1, "Data Import is not supported for tables having more than 2 primary keys");
                this.addResult(rowValues);
                return;
            }
            if (keyMissingCount > 0) {
                entity.setSequencedIdPrimary();
                if (keyMissingCount == 2) {
                    entity.setSequencedIdSecondary();
                }
            }

            boolean beganTransaction = false;
            try {
                beganTransaction = tf.begin(100);
                if(keyMissingCount == 0) {
                    EntityValueBase existing = (EntityValueBase) ed.makeEntityFind()
                            .forUpdate(true).condition(primaryKeys).one();
                    if(existing != null) {
                        Map<String, Object> eValueMap = entity.getEtlValues();
                        eValueMap.forEach((k, v) -> {
                            if(!primaryKeys.containsKey(k)) {
                                existing.put(k, v);
                            }
                        });
                        existing.update();
                    }
                } else {
                    entity.create();
                }
                rowValues.set(1, SUCCESS_MESSAGE);
            } catch (Exception e) {
                String errStr = "Error saving entity " + entity.getPrimaryKeysString();
                LOGGER.error(errStr, e);
                tf.rollback(beganTransaction, errStr, e);
                rowValues.set(1, "Imported Failed: " + e.getMessage());
            } finally {
                tf.commit(beganTransaction);
            }
            this.addResult(rowValues);
        }
        @Override
        public void run() {
            final String threadName = Thread.currentThread().getName();
            System.out.println("NEW THREAD: " + threadName);

            // check for active Transaction
            if (getEcfi().transactionFacade.isTransactionInPlace()) {
                LOGGER.error("THREAD '" + threadName + "': " +
                        "A transaction is in place, trying to commit...");
                try {
                    getEcfi().transactionFacade.destroyAllInThread();
                } catch (Exception e) {
                    LOGGER.error("THREAD '" + threadName + "' STOPPED: " +
                            "Failed to commit in place transaction", e);
                    return;
                }
            }
            ExecutionContextImpl activeEc = getEcfi().activeContext.get();
            if (activeEc != null) {
                LOGGER.error("THREAD '" + threadName + "': " +
                        " there is already an ExecutionContext for user" + activeEc.getUser().getUsername() +
                        " (from" + activeEc.forThreadId + ":" + activeEc.forThreadName + "), destroying..." );
                try {
                    activeEc.destroy();
                } catch (Throwable t) {
                    LOGGER.error("THREAD '" + threadName + "' STOPPED: " +
                            "Error destroying in-place ExecutionContext. STOPPED", t);
                }
            }

            ExecutionContextImpl eci = null;
            try {
                eci = getEcfi().getEci();
                eci.getArtifactExecution().disableAuthz();
                UserFacadeImpl ufi = (UserFacadeImpl) eci.getUser();
                if (this.username != null && !this.username.isEmpty()) {
                    ufi.internalLoginUser(this.username, false);
                } else {
                    ufi.loginAnonymousIfNoUser();
                }
                TransactionFacadeImpl tf = eci.transactionFacade;
                AbstractMap.SimpleEntry<List<String>, EntityValue> task;
                while ((task = this.pollTask()) != null || this.keepAlive.get()) {
                    if (task == null) {
                        if (_wait("waiting for new task"))
                            break;
                        continue;
                    }
                    store(task.getKey(), task.getValue(), tf);
                }
            } finally {
                if (eci != null) eci.destroy();
            }
            int curNumThreads;
            synchronized (this.numThreads) {
                curNumThreads = this.numThreads.decrementAndGet();
            }

            if(curNumThreads <= 1 && !this.keepAlive.get()) {
                _notify(); // notify the main thread
            }
        }
    }
    public List<String> headers = new ArrayList<>(){{add("File name"); add("Result");}};

    private final ExecutionContext ec;

    private final CrURunable crU;
    public DBWorker(ExecutionContext ec, int maxThreads, int numTasksPerThread) {
        this.ec = ec;
        crU = new CrURunable(this.ec, maxThreads, numTasksPerThread);
    }
    public void shutdown() {
        this.crU.shutdown();
    }

    public Collection<List<String>> getResults() {
        return this.crU.results;
    }
    public void setHeader(List<String> headers) {
        if(headers == null || headers.isEmpty()) {
            return;
        }
        if(headers.get(0) == null && headers.get(1) == null) {
            this.headers.addAll(headers.subList(this.headers.size(), headers.size()));
        } else {
            this.headers.addAll(headers);
        }
    }
    public void addResult(String[] resultStrs) {
        if(resultStrs == null || resultStrs.length < 3) {
            return;
        }
        this.crU.addResult(Arrays.asList(resultStrs));
    }
    public void addResult(List<String> resultStrs) {
        if(resultStrs == null || resultStrs.size() < 3) {
            return;
        }
        this.crU.addResult(resultStrs);
    }
    public void submit(List<String> rowValues, EntityValue entity) {
        if(rowValues == null || rowValues.isEmpty() || entity == null) {
            return;
        }
        UserFacade uf = ec.getUser();
        if(entity.isField("ownerPartyId")
                && entity.getNoCheckSimple("ownerPartyId") == null) {
            entity.set("ownerPartyId", uf.getTenantId());
        }
        if(entity.isField("contentDate") && entity.getNoCheckSimple("contentDate") == null) {
            entity.set("contentDate", uf.getNowTimestamp());
        }
        if(entity.isField("userId") && entity.getNoCheckSimple("userId") == null) {
            entity.set("userId", uf.getUserId());
        }

        ArtifactExecutionFacadeImpl aefi = (ArtifactExecutionFacadeImpl) this.ec.getArtifactExecution();
        ArtifactExecutionInfoImpl aeii = new ArtifactExecutionInfoImpl(entity.getEntityName(),
                ArtifactExecutionInfo.AT_ENTITY, ArtifactExecutionInfo.AUTHZA_VIEW, "refresh")
                .setParameters(entity.getEtlValues());
        if (!aefi.isPermitted(aeii, (ArtifactExecutionInfoImpl) aefi.peek(),
                true, false, true, null)) {
            String err = "User " + (crU.username == null ? "[No User]" : crU.username) +
                    " is not authorized for" + aeii.getActionDescription() +
                    " on " + aeii.getTypeDescription() + aeii.getName();
            rowValues.set(1, err);
            this.crU.addResult(rowValues);
            return;
        }


        this.crU.queueTask(rowValues, entity);
   }
}
