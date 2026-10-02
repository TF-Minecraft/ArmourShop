package net.tfminecraft.armourshop.entitlements;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.armourshop.Cache;
import net.tfminecraft.armourshop.objects.PermissionGroupDefinition;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

class PermissionGroupServiceTest {
    static final String COLOURS = PermissionGroupDefinition.KEY_NAME_COLOUR_STOPS;
    static final String BYTES = PermissionGroupDefinition.KEY_MAX_3D_PAIR_BYTES;
    static final String COOLDOWN = PermissionGroupDefinition.KEY_SKIN_TOKEN_COOLDOWN_DAYS;
    Map<String, Integer> previousDefaults;
    List<String> previousKinds;
    List<PermissionGroupDefinition> previousGroups;
    boolean previousHelmet;
    Player player;

    @BeforeEach void setup() {
        previousDefaults = Cache.permissionGroupDefaults; previousKinds = Cache.permissionGroupDefaultSkinKinds;
        previousGroups = Cache.permissionGroups; previousHelmet = Cache.permissionGroupDefaultAllowArmor3dHelmet;
        Cache.permissionGroupDefaults = new HashMap<>(); Cache.permissionGroupDefaultSkinKinds = new ArrayList<>();
        Cache.permissionGroups = new ArrayList<>(); Cache.permissionGroupDefaultAllowArmor3dHelmet = false;
        player = mock(Player.class);
    }

    @AfterEach void restore() {
        Cache.permissionGroupDefaults = previousDefaults; Cache.permissionGroupDefaultSkinKinds = previousKinds;
        Cache.permissionGroups = previousGroups; Cache.permissionGroupDefaultAllowArmor3dHelmet = previousHelmet;
    }

    PermissionGroupDefinition group(String id, String permission, int tier, Map<String, Integer> perks, List<String> kinds, Boolean helmet) {
        var group = new PermissionGroupDefinition(id, permission, id.toUpperCase(Locale.ROOT), tier, true, perks, kinds, helmet);
        Cache.permissionGroups.add(group); return group;
    }

    @Test void definitionsDefensivelyCopyNullablePerksAndKindsAndExposeTheirMetadata() {
        Map<String, Integer> perks = new HashMap<>(Map.of(COLOURS, 4)); List<String> kinds = new ArrayList<>(List.of("book"));
        var group = group("gold", "ranks.gold", 4, perks, kinds, false);
        perks.put(COLOURS, 99); kinds.clear();
        assertEquals("gold", group.getId()); assertEquals("ranks.gold", group.getPermission()); assertEquals("GOLD", group.getDisplayName());
        assertEquals(4, group.getTier()); assertTrue(group.isVisible()); assertEquals(4, group.getPerk(COLOURS, 0));
        assertEquals(12, group.getPerk("missing", 12)); assertTrue(group.hasPerk(COLOURS)); assertFalse(group.hasPerk("missing"));
        assertEquals(List.of("book"), group.getSkinKinds()); assertTrue(group.hasAllowArmor3dHelmet()); assertFalse(group.getAllowArmor3dHelmet(true));
        assertThrows(UnsupportedOperationException.class, () -> group.getPerks().put(COLOURS, 2));
        assertThrows(UnsupportedOperationException.class, () -> group.getSkinKinds().add("gun"));
        var empty = new PermissionGroupDefinition("empty", null, "", 0, false, null, null, null);
        assertEquals(Map.of(), empty.getPerks()); assertEquals(List.of(), empty.getSkinKinds()); assertFalse(empty.isVisible());
        assertFalse(empty.hasAllowArmor3dHelmet()); assertTrue(empty.getAllowArmor3dHelmet(true)); assertFalse(empty.getAllowArmor3dHelmet(false));
    }

    @Test void defaultAccessorsAndUnrankedPlayersUseTheConfiguredDefaults() {
        Cache.permissionGroupDefaults.putAll(Map.of(COLOURS, 2, BYTES, 50000, COOLDOWN, 28));
        Cache.permissionGroupDefaultSkinKinds.add("book"); Cache.permissionGroupDefaultAllowArmor3dHelmet = true;
        assertEquals(2, PermissionGroupService.getDefaultNameColourStops()); assertEquals(50000, PermissionGroupService.getDefaultMax3dPairBytes());
        assertEquals(28, PermissionGroupService.getDefaultSkinTokenCooldownDays()); assertTrue(PermissionGroupService.getDefaultAllowArmor3dHelmet());
        assertEquals(List.of("book"), PermissionGroupService.getDefaultSkinKinds()); PermissionGroupService.getDefaultSkinKinds().clear();
        assertEquals(2, PermissionGroupService.getNameColourStops(null)); assertEquals(50000, PermissionGroupService.getMax3dPairBytes(null));
        assertEquals(28, PermissionGroupService.getSkinTokenCooldownDays(null)); assertTrue(PermissionGroupService.getAllowArmor3dHelmet(null));
        assertEquals(List.of("book"), PermissionGroupService.getSkinKinds(null)); PermissionGroupService.getSkinKinds(null).clear();
        assertEquals(List.of("book"), Cache.permissionGroupDefaultSkinKinds);
        assertEquals(28, PermissionGroupService.getSkinTokenCooldownDays(player)); assertTrue(PermissionGroupService.getAllowArmor3dHelmet(player));
    }

    @Test void missingConfiguredPairBudgetStillUsesTheDocumentedBuiltInDefault() {
        assertEquals(0, PermissionGroupService.getDefaultNameColourStops()); assertEquals(-1, PermissionGroupService.getDefaultSkinTokenCooldownDays());
        assertEquals(30720, PermissionGroupService.getDefaultMax3dPairBytes());
        var inherited = group("inherited", "ranks.inherited", 1, Map.of(), List.of(), null);
        assertAll(
            () -> assertEquals(30720, PermissionGroupService.getMax3dPairBytes(null)),
            () -> assertEquals(30720, PermissionGroupService.getMax3dPairBytes(player)),
            () -> assertEquals(30720, PermissionGroupService.groupPerkOrDefault(null, BYTES)),
            () -> assertEquals(30720, PermissionGroupService.groupPerkOrDefault(inherited, BYTES))
        );
    }

    @Test void maximumBudgetsOnlyUseMatchingPermissionsAndNeverLowerTheDefaults() {
        Cache.permissionGroupDefaults.putAll(Map.of(COLOURS, 2, BYTES, 30000));
        group("null", null, 0, Map.of(COLOURS, 100), List.of(), null);
        group("blank", " ", 0, Map.of(COLOURS, 100), List.of(), null);
        group("unmatched", "ranks.unmatched", 9, Map.of(COLOURS, 100), List.of(), null);
        group("weak", "ranks.weak", 1, Map.of(COLOURS, 1, BYTES, 20000), List.of(), null);
        group("gold", "ranks.gold", 3, Map.of(COLOURS, 5, BYTES, 60000), List.of(), null);
        group("empty", "ranks.empty", 4, Map.of(), List.of(), null);
        when(player.hasPermission("ranks.weak")).thenReturn(true); when(player.hasPermission("ranks.gold")).thenReturn(true);
        when(player.hasPermission("ranks.empty")).thenReturn(true);
        assertEquals(5, PermissionGroupService.getNameColourStops(player)); assertEquals(60000, PermissionGroupService.getMax3dPairBytes(player));
    }

    @Test void highestMatchingTierInheritsCooldownKindsAndHelmetFlagFromLowerTiers() {
        Cache.permissionGroupDefaults.put(COOLDOWN, 40);
        Cache.permissionGroupDefaultSkinKinds = new ArrayList<>(Arrays.asList(" BOOK ", "", null));
        group("no-node", null, -3, Map.of(), List.of(), null); group("blank-node", " ", -2, Map.of(), List.of(), null);
        group("lower", "ranks.lower", 1, Map.of(COOLDOWN, 28), Arrays.asList(" BOW ", "book", " ", null), false);
        group("gold", "ranks.gold", 2, Map.of(COOLDOWN, 14), List.of(" ITEM_3D "), true);
        group("highest", "ranks.highest", 3, Map.of(), List.of("SHIELD"), null);
        group("above", "ranks.above", 4, Map.of(COOLDOWN, 0), List.of("gun"), false);
        when(player.hasPermission("ranks.gold")).thenReturn(true); when(player.hasPermission("ranks.highest")).thenReturn(true);
        assertEquals(14, PermissionGroupService.getSkinTokenCooldownDays(player)); assertTrue(PermissionGroupService.getAllowArmor3dHelmet(player));
        assertEquals(List.of("book", "bow", "item_3d", "shield"), PermissionGroupService.getSkinKinds(player));
        // Inheritance is tier based, even without the lower tier's permission node.
        verify(player, atLeastOnce()).hasPermission("ranks.lower");
    }

    @Test void missingInheritedValuesFallBackAndExplicitHigherValuesOverride() {
        Cache.permissionGroupDefaults.put(COOLDOWN, 8); Cache.permissionGroupDefaultAllowArmor3dHelmet = true;
        group("empty", "ranks.empty", 3, Map.of(), List.of(), null); when(player.hasPermission("ranks.empty")).thenReturn(true);
        assertEquals(8, PermissionGroupService.getSkinTokenCooldownDays(player)); assertTrue(PermissionGroupService.getAllowArmor3dHelmet(player));
        group("explicit", "ranks.explicit", 3, Map.of(COOLDOWN, -1), List.of(), false);
        group("lower", "ranks.lower", 1, Map.of(COOLDOWN, 21), List.of(), true);
        assertEquals(-1, PermissionGroupService.getSkinTokenCooldownDays(player)); assertFalse(PermissionGroupService.getAllowArmor3dHelmet(player));
    }

    @Test void catalogGroupPerksUseExplicitValuesThenDefaults() {
        Cache.permissionGroupDefaults.put(COLOURS, 2); Cache.permissionGroupDefaults.put(COOLDOWN, 10);
        Cache.permissionGroupDefaultAllowArmor3dHelmet = true;
        var explicit = group("explicit", "rank", 1, Map.of(COLOURS, 7, COOLDOWN, 3), List.of(), false);
        var inherited = group("inherited", "rank.other", 2, Map.of(), List.of(), null);
        assertEquals(2, PermissionGroupService.groupPerkOrDefault(null, COLOURS));
        assertEquals(10, PermissionGroupService.groupPerkOrDefault(null, COOLDOWN));
        assertEquals(7, PermissionGroupService.groupPerkOrDefault(explicit, COLOURS));
        assertEquals(3, PermissionGroupService.groupPerkOrDefault(explicit, COOLDOWN));
        assertEquals(2, PermissionGroupService.groupPerkOrDefault(inherited, COLOURS));
        assertEquals(0, PermissionGroupService.groupPerkOrDefault(inherited, "unconfigured"));
        assertTrue(PermissionGroupService.groupAllowArmor3dHelmetOrDefault(null));
        assertTrue(PermissionGroupService.groupAllowArmor3dHelmetOrDefault(inherited));
        assertFalse(PermissionGroupService.groupAllowArmor3dHelmetOrDefault(explicit));
    }
}
