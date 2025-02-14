package cn.zbx1425.mtrsteamloco.render.rail;

import cn.zbx1425.sowcer.batch.BatchManager;
import cn.zbx1425.sowcer.batch.EnqueueProp;
import cn.zbx1425.sowcer.batch.ShaderProp;
import cn.zbx1425.sowcer.model.Model;
import cn.zbx1425.sowcer.util.GLStateCapture;
import com.mojang.math.Matrix4f;
import mtr.data.Rail;
import mtr.data.RailType;
import mtr.render.RenderTrains;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.*;

public class RailRenderDispatcher {

    private final HashMap<ChunkPos, RenderRailChunk> renderChunks = new HashMap<>();
    private final HashMap<Rail, RailSpan> railSpans = new HashMap<>();
    private final HashSet<Rail> currentFrameRails = new HashSet<>();

    private final HashSet<RenderRailChunk> priorityRebuildChunks = new HashSet<>();
    private final LinkedList<RenderRailChunk> renderChunkList = new LinkedList<>();
    private int lastRebuildCycleIndex = -1;

    public static boolean isHoldingRailItem = false;

    protected Model railModel;

    public void setModel(Model railModel) {
        this.railModel = railModel;
    }

    private void addRail(Rail rail) {
        if (railSpans.containsKey(rail)) return;
        RailSpan railSpan = new RailSpan(rail);
        for (ChunkPos reg : railSpan.coveredChunks) {
            if (!renderChunks.containsKey(reg)) {
                GLStateCapture stateCapture = new GLStateCapture();
                stateCapture.capture();
                RenderRailChunk newChunk = new RenderRailChunk(reg, railModel);
                renderChunks.put(reg, newChunk);
                renderChunkList.add(newChunk);
                stateCapture.restore();
            }
            RenderRailChunk chunk = renderChunks.get(reg);
            chunk.containingRails.add(railSpan);
            priorityRebuildChunks.add(chunk);
        }
        railSpans.put(rail, railSpan);
    }

    private void removeRail(Rail rail) {
        if (!railSpans.containsKey(rail)) return;
        RailSpan railSpan = railSpans.get(rail);
        for (ChunkPos reg : railSpan.coveredChunks) {
            RenderRailChunk chunk = renderChunks.get(reg);
            chunk.containingRails.remove(railSpan);
            priorityRebuildChunks.add(chunk);
        }
        railSpans.remove(rail);
    }

    public void registerRail(Rail rail) {
        if (rail.railType == RailType.NONE) return;
        currentFrameRails.add(rail);
    }

    private static final Random random = new Random();

    public void updateAndEnqueueAll(Level level, BatchManager batchManager, Matrix4f viewMatrix) {
        isHoldingRailItem = Minecraft.getInstance().player != null && RenderTrains.isHoldingRailRelated(Minecraft.getInstance().player);

        HashSet<Rail> railsToAdd = new HashSet<>(currentFrameRails);
        railsToAdd.removeAll(railSpans.keySet());
        for (Rail rail : railsToAdd) addRail(rail);
        HashSet<Rail> railsToRemove = new HashSet<>(railSpans.keySet());
        railsToRemove.removeAll(currentFrameRails);
        for (Rail rail : railsToRemove) removeRail(rail);
        currentFrameRails.clear();
        for (Iterator<Map.Entry<ChunkPos, RenderRailChunk>> it = renderChunks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ChunkPos, RenderRailChunk> entry = it.next();
            if (entry.getValue().containingRails.size() == 0) {
                entry.getValue().close();
                priorityRebuildChunks.remove(entry.getValue());
                renderChunkList.remove(entry.getValue());
                it.remove();
            }
        }

        if (priorityRebuildChunks.size() > 0) {
            Optional<RenderRailChunk> chunkToRebuild = priorityRebuildChunks.stream().findFirst();
            chunkToRebuild.get().rebuildBuffer(level);
            priorityRebuildChunks.remove(chunkToRebuild.get());
        } else if (renderChunkList.size() > 0) {
            // Cycle through each chunk and rebuild the mesh every frame.
            // As for performance impact, I suppose if it's to be a lag spike anyway,
            // it won't hurt to spread it out so that it's more noticeable.
            lastRebuildCycleIndex++;
            if (lastRebuildCycleIndex >= renderChunkList.size()) lastRebuildCycleIndex = 0;
            renderChunkList.get(lastRebuildCycleIndex).rebuildBuffer(level);
        }

        for (RenderRailChunk chunk : renderChunks.values()) {
            chunk.renderAll(batchManager, EnqueueProp.DEFAULT, new ShaderProp().setViewMatrix(viewMatrix));
        }
    }
}
