package icyllis.modernui.mc.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackCompatibility;

import java.nio.file.Files;
import java.nio.file.Path;

/** Checks both pack types using Minecraft 1.21.8's actual metadata and compatibility APIs. */
public final class PackMetadataCheck {

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of(System.getProperty("modernui.projectRoot"));
        Path metadataPath = projectRoot.resolve("neoforge/src/main/resources/pack.mcmeta");
        var metadata = JsonParser.parseString(Files.readString(metadataPath)).getAsJsonObject()
                .get(PackMetadataSection.TYPE.name());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Before 1.21.9, both pack types share this codec. supported_formats is
        // leniently optional, so require it to survive decoding instead of
        // silently accepting an unrecognized range as pack_format alone.
        var section = PackMetadataSection.TYPE.codec().parse(JsonOps.INSTANCE, metadata)
                .getOrThrow(message -> new AssertionError("Invalid pack metadata: " + message));
        var range = section.supportedFormats()
                .orElseThrow(() -> new AssertionError("Minecraft did not decode supported_formats"));
        if (!range.isValueInRange(section.packFormat())) {
            throw new AssertionError("Declared main format " + section.packFormat()
                    + " is outside " + range + "; Minecraft would discard the range");
        }
        for (PackType packType : PackType.values()) {
            int currentFormat = SharedConstants.getCurrentVersion().packVersion(packType);
            var compatibility = PackCompatibility.forVersion(range, currentFormat);
            if (compatibility != PackCompatibility.COMPATIBLE) {
                throw new AssertionError(packType + " format " + currentFormat
                        + " is " + compatibility + " for " + range);
            }
            System.out.println(packType + " metadata accepted; " + currentFormat + " in " + range);
        }
    }
}
