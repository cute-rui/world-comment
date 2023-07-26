package cn.zbx1425.worldcomment.data.network;

import cn.zbx1425.worldcomment.data.CommentEntry;
import cn.zbx1425.worldcomment.data.Database;
import cn.zbx1425.worldcomment.network.PacketSubmitCommentC2S;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SubmitDispatcher {

    private static final Executor NETWORK_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Long2ObjectMap<SubmitJob> pendingJobs = new Long2ObjectOpenHashMap<>();

    public static long addJob(CommentEntry comment, Path imagePath, Consumer<Exception> callback) {
        synchronized (pendingJobs) {
            long jobId = Database.SNOWFLAKE.nextId();
            SubmitJob job = new SubmitJob(comment, imagePath, callback);
            pendingJobs.put(jobId, job);
            if (imagePath != null) {
                NETWORK_EXECUTOR.execute(() -> {
                    try {
                        job.setImage(ImageHost.uploadImage(imagePath));
                        trySendPackage(jobId);
                    } catch (Exception ex) {
                        if (job.callback != null) job.callback.accept(ex);
                        removeJob(jobId);
                    }
                });
            }
            return jobId;
        }
    }

    public static void placeJobAt(long jobId, BlockPos blockPos) {
        synchronized (pendingJobs) {
            if (!pendingJobs.containsKey(jobId)) return;
            pendingJobs.get(jobId).setLocation(blockPos);
            trySendPackage(jobId);
        }
    }

    public static void removeJob(long jobId) {
        synchronized (pendingJobs) {
            pendingJobs.remove(jobId);
        }
    }

    private static void trySendPackage(long jobId) {
        SubmitJob job = pendingJobs.get(jobId);
        if (job.isReady()) {
            PacketSubmitCommentC2S.ClientLogics.send(job.comment);
            if (job.callback != null) job.callback.accept(null);
            removeJob(jobId);
        }
    }
}
