package im.ringlo.recall.test.datagen

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import im.ringlo.recall.api.data.RecallContentProvider
import im.ringlo.recall.api.data.RecallDataOutput
import im.ringlo.recall.api.scene.SceneActions
import im.ringlo.recall.content.ContentSnapshot
import im.ringlo.recall.test.RecallTestMod
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.PackOutput
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.item.Items

class RecallTestContentProvider(output: PackOutput, resources: ResourceManager) :
    RecallContentProvider(output, RecallTestMod.ID, resources) {
    override fun build(content: RecallDataOutput) {
        content.guide(
            ContentSnapshot.Guide(
                id = content.id("field_notes"),
                title = "Abyss Field Notes",
                subtitle = "A server-authored guide for the next descent.",
                maps = listOf(content.id("field_map"), content.id("lower_route")),
                icon = BuiltInRegistries.ITEM.getKey(Items.WRITABLE_BOOK),
            )
        )
        content.map(
            ContentSnapshot.MapDefinition(
                id = content.id("field_map"),
                title = "Surface Route",
                nodes =
                    listOf(
                        ContentSnapshot.MapNode(
                            id = content.id("surface"),
                            articleIds = listOf(content.id("getting_started")),
                            x = 150f,
                            y = 220f,
                            title = "Surface Camp",
                            summary = "The first landmark before the descent.",
                            item = BuiltInRegistries.ITEM.getKey(Items.COMPASS),
                        ),
                        ContentSnapshot.MapNode(
                            id = content.id("rules"),
                            articleIds = listOf(content.id("field_rules")),
                            x = 470f,
                            y = 220f,
                            title = "Field Rules",
                            summary = "Rules for every layer.",
                            item = BuiltInRegistries.ITEM.getKey(Items.BOOK),
                        ),
                    ),
                edges = listOf(ContentSnapshot.MapEdge(content.id("surface"), content.id("rules"))),
            )
        )
        content.map(
            ContentSnapshot.MapDefinition(
                id = content.id("lower_route"),
                title = "Lower Route",
                nodes =
                    listOf(
                        ContentSnapshot.MapNode(
                            id = content.id("camp"),
                            articleIds = listOf(content.id("field_rules")),
                            x = 170f,
                            y = 245f,
                            title = "Return Point",
                            summary = "A marked point on the lower route.",
                            item = Identifier.parse("minecraft:lodestone"),
                        ),
                        ContentSnapshot.MapNode(
                            id = content.id("echo"),
                            articleIds = listOf(content.id("getting_started")),
                            x = 470f,
                            y = 245f,
                            title = "Echo Chamber",
                            summary = "A second article can be opened from this node.",
                            item = Identifier.parse("minecraft:amethyst_shard"),
                        ),
                    ),
                edges =
                    listOf(
                        ContentSnapshot.MapEdge(content.id("camp"), content.id("echo"), "route")
                    ),
                startNode = content.id("camp"),
            )
        )
        content.scene(content.id("descent_intro"), zeroLayer = 1) {
            action(SceneActions.TITLE) { addProperty("text", "Descent Preview") }
            action(SceneActions.REVEAL, duration = .6f) {
                addProperty("group", "platform")
                selection(0, 0, 0, 4, 0, 2)
                vector("offset", 0, -1, 0)
            }
            action(SceneActions.KEYFRAME, at = .7f)
            action(SceneActions.REVEAL, at = .7f, duration = .6f) {
                addProperty("group", "equipment")
                selection(0, 1, 0, 1, 1, 1)
                vector("offset", 0, 1, 0)
            }
            action(SceneActions.REVEAL, at = 1.2f, duration = .6f) {
                addProperty("group", "water")
                selection(3, 1, 1, 4, 1, 1)
            }
            action(SceneActions.KEYFRAME, at = 1.8f)
            action(SceneActions.REVEAL, at = 1.8f, duration = .7f) {
                addProperty("group", "route")
                selection(2, -1, 3, 2, 1, 5)
            }
            action(SceneActions.MOVE, at = 3f, duration = 1.5f) {
                addProperty("group", "route")
                vector("offset", 0, -1, 0)
            }
            action(SceneActions.KEYFRAME, at = 4.5f)
            action(SceneActions.HIGHLIGHT, at = 4.5f, duration = 3f) {
                addProperty("group", "route")
            }
            action(SceneActions.OVERLAY_SHOW_TEXT, at = 4.5f, duration = 3f) {
                addProperty("text", "The route continues below the surface.")
                vector("position", 2.5, 0, 4.5)
            }
            action(SceneActions.HIDE, at = 7.5f, duration = .6f) {
                addProperty("group", "route")
                vector("offset", 0, -1, 0)
            }
            action(SceneActions.KEYFRAME, at = 8.5f)
            action(SceneActions.REVEAL, at = 8.5f, duration = .6f) { addProperty("group", "route") }
            action(SceneActions.WAIT, at = 9.1f, duration = .9f)
        }
    }

    private fun JsonObject.vector(key: String, vararg coordinates: Number) {
        add(key, JsonArray().apply { coordinates.forEach(::add) })
    }

    private fun JsonObject.selection(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) {
        add(
            "selection",
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        vector("from", x1, y1, z1)
                        vector("to", x2, y2, z2)
                    }
                )
            },
        )
    }
}
