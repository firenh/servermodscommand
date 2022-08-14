package fireopal.servermodscommand;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.compress.utils.Lists;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ModsCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            register(dispatcher, registryAccess, environment);
        });
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, RegistrationEnvironment environment) {
        dispatcher.register(
            CommandManager.literal(ServerModsCommand.getCommandLiteral())
                .executes(context -> run(context, false))
                .then(CommandManager.literal("all")
                    .executes(context -> run(context, true))
                )
                .then(CommandManager.literal("details")
                    .then(CommandManager.argument("mod_id", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            getAllModIds().forEach(id -> {
                                builder.suggest(id);
                            });

                            return builder.buildFuture();
                        })
                        .executes(context -> run(context, StringArgumentType.getString(context, "mod_id")))
                    )
                )
                
                    
        );
    }

    private static List<String> getAllModIds() {
        return FabricLoader.getInstance().getAllMods().stream()
            .map(mod -> mod.getMetadata().getId())
            .collect(Collectors.toList());
    }

    private static int run(CommandContext<ServerCommandSource> context, boolean all) {
        List<MutableText> modTexts = FabricLoader.getInstance().getAllMods().stream()
            .filter(mod -> {
                if (all) return true;

                ModMetadata metadata = mod.getMetadata();
                return !isTechnical(metadata);
            })
            .map(mod -> {
                ModMetadata metadata = mod.getMetadata();
                String modNameTemp = metadata.getName();
                String modId = metadata.getId();

                if (modNameTemp.length() == 0) {
                    modNameTemp = modId;
                }

                final String modName = modNameTemp;

                String desc = metadata.getDescription();
                List<String> authors = metadata.getAuthors().stream().map(person -> person.getName()).collect(Collectors.toList());
                String version = metadata.getVersion().getFriendlyString();

                MutableText authorText = Text.of("").copyContentOnly();

                for (int i = 0; i < authors.size(); i += 1) {
                    authorText.append(
                        Text.of(authors.get(i)).copyContentOnly().styled(style -> style.withItalic(true).withColor(Formatting.WHITE))
                    );

                    if (i + 1 < authors.size()) {
                        authorText.append(
                            Text.of(", ").copyContentOnly().styled(style -> style.withColor(Formatting.GRAY))
                        );
                    }
                }

                MutableText hoverText = MutableText.of(Text.of(modId).getContent()).append(
                    MutableText.of(Text.of(" " + version).getContent()).styled(style0 -> style0.withColor(Formatting.GRAY))
                );

                if (desc.length() > 0) {
                    hoverText.append(MutableText.of(Text.of("\n\n" + desc).getContent()).styled(style1 -> style1.withColor(Formatting.GRAY).withItalic(true)));
                }

                if (authorText.toString().length() > 0) {
                    hoverText.append("\n\n");
                    hoverText.append(authorText);
                }

                MutableText modText = Text.of(modName).copyContentOnly().styled(style -> style.withColor(isTechnical(metadata) ? Formatting.DARK_GREEN : Formatting.GREEN)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/mods details " + modId))
                
                );

                return modText;
            })
            .collect(Collectors.toList());

        final int modTotal = modTexts.size();
        MutableText text = Text.of("").copyContentOnly();

        for (int i = 0; i < modTotal; i += 1) {
            text.append(
                Text.of(" - ").copyContentOnly().styled(style -> style.withItalic(true).withColor(Formatting.WHITE))
            );

            text.append(
                modTexts.get(i).styled(style -> style)
            );

            text.append(
                Text.of("\n").copyContentOnly().styled(style -> style.withItalic(true).withColor(Formatting.WHITE))
            );
        }

        ServerCommandSource source = context.getSource();
        Text feedback = Text.translatable("servermodscommand.command.mods.feedback", modTotal, text);

        source.sendFeedback(feedback, false);

        return modTotal;
    }

    private static int run(CommandContext<ServerCommandSource> context, String modid) {
        Optional<ModContainer> possibleMod = FabricLoader.getInstance().getModContainer(modid);
        ServerCommandSource source = context.getSource();

        if (possibleMod.isEmpty()) {
            source.sendError(Text.translatable("servermodscommand.command.mods.modnotfound", modid));
            return 0;
        }

        ModContainer mod = possibleMod.get();
        ModMetadata metadata = mod.getMetadata();
        MutableText text = Text.empty();
        String modName = metadata.getName();
        String modId = metadata.getId();
        String desc = metadata.getDescription();
        List<String> licenses = metadata.getLicense().stream().collect(Collectors.toList());
        List<Text> contactInfo = contactInfo(metadata.getContact().asMap());
        Collection<Person> authors =  metadata.getAuthors();
        Collection<Person> contributors =  metadata.getContributors();

        if (modName.length() == 0) {
            modName = modId;
        } 
    
        text.append(Text.of("\n" + modName).copyContentOnly().styled(style -> style.withColor(Formatting.GREEN)));

        if (!modName.equals(modId)) {
            text.append(Text.of(" (" + modId + ")\n").copyContentOnly().styled(style -> style.withColor(Formatting.GRAY)));
        }

        text.append(Text.translatable("servermodscommand.command.mods.version", metadata.getVersion().getFriendlyString()));
        
        if (licenses.size() > 0) { 
            text.append(Text.of("\n"));
            text.append(Text.translatable("servermodscommand.command.mods.license"));

            for (int i = 0; i < licenses.size(); i += 1) {
                text.append(Text.of(licenses.get(i) + (i + 1 < licenses.size() ? ", " : "")));
            }
        }

        if (desc.length() > 0) text.append(Text.of("\n\n" + desc).copyContentOnly().styled(style -> style.withItalic(true)));

        if (contactInfo.size() > 0) {
            text.append(Text.of("\n\n"));
            text.append(Text.translatable("servermodscommand.command.mods.contactinfo").styled(style -> style.withUnderline(true)));

            for (Text c : contactInfo) {
                text.append(Text.of("\n - ").copy().append(c));
            }
        }

        authors(authors, text, "servermodscommand.command.mods.authors");
        authors(contributors, text, "servermodscommand.command.mods.contributors");

        text.append(Text.of("\n"));











        source.sendFeedback(text, false);

        return 1;
    }

    private static void authors(Collection<Person> authors, MutableText text, String key) {
        if (authors.size() == 0) return;

        text.append(Text.of("\n\n"));
        text.append(Text.translatable(key).styled(style -> style.withUnderline(true)));

        for (Person p : authors) {
            MutableText author = Text.of("\n   " + p.getName()).copy();
            List<Text> cinfo = contactInfo(p.getContact().asMap());

            if (cinfo.size() > 0) {
                MutableText authorContact = Text.of(":").copy();

                for (Text c : cinfo) {
                    authorContact.append(Text.of("\n    - ").copy().append(c));
                }

                author.append(authorContact);
            }

            text.append(author);
        }
    }

    private static List<Text> contactInfo(Map<String, String> map) {
        List<Text> list = Lists.newArrayList();
        
        map.forEach((key, valb) -> {
            String valw = valb;
            
            boolean bl = false;

            for (int i = 0; i < valw.length() - 1; i += 1) {
                if (valw.charAt(i) == '/' && valw.charAt(i + 1) == '/') {
                    bl = true;
                    break;
                }
            }

            if (bl == false) {
                valw = "http://" + valw;
            }

            if (!valw.endsWith("/")) {
                valw = valw + "/";
            }

            final String val = valw;

            list.add(Text.translatable(
                "servermodscommand.command.mods.contact", 
                Text.translatable(key),
                Text.literal(valb).copy().styled(style -> style
                    .withUnderline(true)
                    .withColor(Formatting.GREEN)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("servermodscommand.command.mods.followlink")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, val))
                )
            ));
        });

        return list;
    }

    private static boolean isTechnical(ModMetadata metadata) {
        String id = metadata.getId();
        if (id.equals("java") || id.equals("minecraft")) return true;
        if (metadata.getAuthors().stream().collect(Collectors.toList()).get(0).getName().equals("FabricMC")) return true;

        return false;
    }
}
