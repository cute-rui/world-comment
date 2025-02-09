package cn.zbx1425.worldcomment.data.sync;

import cn.zbx1425.worldcomment.data.CommentCache;
import cn.zbx1425.worldcomment.data.CommentEntry;
import it.unimi.dsi.fastutil.longs.Long2ObjectSortedMap;

import java.io.IOException;
import java.nio.file.Path;

public interface Synchronizer {

    void kvWriteEntry(CommentEntry trustedEntry);

    void notifyUpdate(CommentEntry trustedEntry);

    void notifyInsert(CommentEntry newEntry);

    void kvReadAllInto(CommentCache comments) throws IOException;

    void kvWriteAll(Long2ObjectSortedMap<CommentEntry> timeIndex);
}
