package cn.zbx1425.worldcomment.data.persist;

import cn.zbx1425.worldcomment.data.Snowflake;
import cn.zbx1425.worldcomment.data.synchronizer.SingletonSynchronizer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;

public class Database {

    public static final Snowflake SNOWFLAKE = new Snowflake();

    public final MinecraftServer server;
    public final Path basePath;

    public boolean isHost = true;

    public final CommentTable comments;

    public Database(MinecraftServer server) {
        this.server = server;
        this.basePath = Path.of(server.getWorldPath(LevelResource.ROOT).toString(), "world-comment");
        comments = new CommentTable(this, new SingletonSynchronizer());
    }

    public void load() throws IOException {
        comments.load();
    }
}
