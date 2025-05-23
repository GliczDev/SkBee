package com.shanebeestudios.skbee.elements.other.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.paperlib.PaperLib;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import com.shanebeestudios.skbee.SkBee;
import com.shanebeestudios.skbee.api.skript.base.Effect;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Name("Chunk - Load/Unload")
@Description({"Load or unload a chunk.",
    "**Options**:",
    "- `%number%,[ ]%number%` = Represents the X/Z coords of a chunk. Not to be confused with a location. " +
        "Chunk coords are essentially a location divided by 16, example: Chunk 1/1 = Location 16/16",
    "- `async` = Will load the chunk off the main thread (Requires PaperMC). Your code will halt whilst waiting for the chunk to load.",
    "- `with ticket` = Will add a ticket to the chunk, preventing it from unloading until you explicitly unload it or the server stops.",
    "- `without saving` = Will prevent the chunk from saving when unloading."})
@Examples({"load chunk at 1,1 in world \"world\"",
    "load chunk at location(1,1,1, world \"world\")",
    "load chunk at 150,150 in world \"world\"",
    "load chunk at 150,150 in world \"world\" with ticket",
    "async load chunk at {_loc}",
    "async load chunk at 100,100 in world \"world\"",
    "async load chunk at 1,1 in world of player with ticket",
    "unload chunk at 1,1 in world \"world\""})
@Since("1.17.0, 2.11.0 (async)")
public class EffLoadChunk extends Effect {

    private static final SkBee PLUGIN = SkBee.getPlugin();

    static {
        Skript.registerEffect(EffLoadChunk.class,
            // Chunk coords
            "[:async] load chunk at %number%,[ ]%number% (in|of) [world] %world% [ticket:with ticket]",
            "unload chunk at %number%,[ ]%number% (in|of) [world] %world%",

            // Location
            "[:async] load chunk at %location% [ticket:with ticket]",
            "unload chunk at %location% [nosave:without saving]",

            // Chunk
            "unload %chunks% [nosave:without saving]");
    }

    private int pattern;
    private boolean ticket;
    private boolean unload;
    private boolean isAsync;
    private boolean save;
    private Expression<Number> x;
    private Expression<Number> z;
    private Expression<World> world;
    private Expression<Location> location;
    private Expression<Chunk> chunks;

    @SuppressWarnings("unchecked")
    @Override
    public boolean init(Expression<?>[] exprs, int pattern, Kleenean kleenean, ParseResult parseResult) {
        this.pattern = pattern;
        this.unload = pattern != 0 && pattern != 2;
        this.ticket = parseResult.hasTag("ticket");
        this.isAsync = parseResult.hasTag("async");
        this.save = !parseResult.hasTag("nosave");

        if (pattern <= 1) {
            this.x = (Expression<Number>) exprs[0];
            this.z = (Expression<Number>) exprs[1];
            this.world = (Expression<World>) exprs[2];
        } else if (pattern <= 3) {
            this.location = (Expression<Location>) exprs[0];
        } else {
            this.chunks = (Expression<Chunk>) exprs[0];
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        TriggerItem next = getNext();

        if (pattern < 4) {
            int x = 0;
            int z = 0;

            World world = Bukkit.getWorlds().getFirst();

            if (this.x != null) {
                Number xSingle = this.x.getSingle(event);
                if (xSingle == null) {
                    return next;
                }
                x = xSingle.intValue();
            }
            if (this.z != null) {
                Number zSingle = this.z.getSingle(event);
                if (zSingle == null) {
                    return next;
                }
                z = zSingle.intValue();
            }
            if (this.world != null) {
                world = this.world.getSingle(event);
                if (world == null) {
                    return next;
                }
            }
            if (this.location != null) {
                Location location = this.location.getSingle(event);
                if (location != null) {
                    x = location.getBlockX() >> 4;
                    z = location.getBlockZ() >> 4;
                    world = location.getWorld();
                }
            }

            if (this.unload) {
                world.removePluginChunkTicket(x, z, PLUGIN);
                world.unloadChunk(x, z, this.save);
            } else {
                if (this.isAsync) {
                    // Let's save you guys for later after the chunk has loaded
                    Object localVars = Variables.removeLocals(event);

                    // If running paper, get chunk async, otherwise will run sync
                    PaperLib.getChunkAtAsync(world, x, z).thenAccept(chunk -> {
                        if (this.ticket) chunk.addPluginChunkTicket(PLUGIN);

                        // re-set local variables
                        if (localVars != null) Variables.setLocalVariables(event, localVars);

                        // walk next trigger
                        if (next != null) TriggerItem.walk(next, event);

                        // remove local vars as we're now done
                        Variables.removeLocals(event);
                    });
                    return null;
                } else {
                    world.getChunkAt(x, z);
                    if (this.ticket) world.addPluginChunkTicket(x, z, PLUGIN);
                }
            }
        } else {
            for (Chunk chunk : this.chunks.getArray(event)) {
                chunk.removePluginChunkTicket(PLUGIN);
                chunk.unload(this.save);
            }
        }
        return next;
    }

    @Override
    public @NotNull String toString(@Nullable Event e, boolean d) {
        String load = this.unload ? "unload" : ((this.isAsync ? "async " : "") + "load");
        String chunk = switch (pattern) {
            case 2, 3 -> "at " + this.location.toString(e, d);
            case 4 -> this.chunks.toString(e, d);
            default -> "at " + this.x.toString(e, d) + "," + this.z.toString(e, d);
        };
        String ticket = this.ticket ? " with ticket" : !this.save ? " witout saving" : "";
        return String.format("%s chunk %s %s", load, chunk, ticket);
    }

}
