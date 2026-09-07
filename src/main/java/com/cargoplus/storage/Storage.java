package com.cargoplus.storage;

import com.cargoplus.model.UserData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Storage {
    private final File file;
    private final Map<UUID, UserData> users = new LinkedHashMap<>();

    public Storage(File dataFolder, String fileName) { this.file = new File(dataFolder, fileName); }

    public synchronized void load() throws IOException {
        users.clear();
        if (!file.exists()) {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("users");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String name = section.getString(key + ".name", "");
                String group = section.getString(key + ".group", "membro");
                String nicknameColor = section.getString(key + ".nickname-color", "");
                users.put(uuid, new UserData(uuid, name, group, nicknameColor));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public synchronized void save() throws IOException { saveSnapshot(users); }

    public void saveSnapshot(Map<UUID, UserData> snapshot) throws IOException {
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (UserData user : snapshot.values()) {
            String path = "users." + user.uuid();
            yaml.set(path + ".name", user.name());
            yaml.set(path + ".group", user.group());
            if (!user.nicknameColor().isBlank()) yaml.set(path + ".nickname-color", user.nicknameColor());
        }
        File parent = file.getParentFile() == null ? new File(".") : file.getParentFile();
        File temp = new File(parent, file.getName() + ".tmp");
        yaml.save(temp);
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized UserData get(UUID uuid) { return users.get(uuid); }
    public synchronized void put(UserData user) { users.put(user.uuid(), user); }
    public synchronized Map<UUID, UserData> snapshot() { return Map.copyOf(users); }
}
