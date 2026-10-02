package net.tfminecraft.armourshop.api;

import static net.tfminecraft.armourshop.api.ProvinceSystemClient.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonParser;
import java.util.*;
import net.tfminecraft.armourshop.pack.model.PackPaths;
import org.junit.jupiter.api.Test;

class ProvinceSystemClientTest {
    @Test void resultFactoriesPreservePayloadsAndDefensiveCopies() {
        assertNotNull(new ProvinceSystemClient());
        assertTrue(SimpleResult.success().ok);
        assertEquals("no", SimpleResult.fail("no").error);
        var code = new ActiveCode("abc", "uuid", "name", "created", "expires");
        var codes = new ArrayList<>(List.of(code));
        var active = ActiveCodesResult.success(codes);
        codes.clear();
        assertEquals(List.of(code), active.codes);
        assertThrows(UnsupportedOperationException.class, () -> active.codes.clear());
        assertTrue(ActiveCodesResult.success(null).codes.isEmpty());
        assertFalse(ActiveCodesResult.fail("no").ok);
        assertTrue(ListResult.success(null).submissions.isEmpty());
        assertFalse(ListResult.fail("no").ok);
        var ids = new ArrayList<>(List.of("one"));
        var applied = AppliedResult.success(ids);
        var deletable = DeletableListResult.success(ids);
        ids.clear();
        assertEquals(List.of("one"), applied.applied);
        assertEquals(List.of("one"), deletable.ids);
        assertThrows(UnsupportedOperationException.class, () -> applied.applied.add("two"));
        assertTrue(AppliedResult.success(null).applied.isEmpty());
        assertTrue(DeletableListResult.success(null).ids.isEmpty());
        assertFalse(AppliedResult.fail("no").ok);
        assertFalse(DeletableListResult.fail("no").ok);
        assertArrayEquals(new byte[]{1}, DownloadResult.success(new byte[]{1}).data);
        assertFalse(DownloadResult.fail("no").ok);
        var catalog = CatalogPushResult.success(1, 2, 3, "now");
        assertTrue(catalog.ok); assertEquals(1, catalog.categories); assertEquals(2, catalog.skinSets);
        assertEquals(3, catalog.scrolls); assertEquals("now", catalog.updatedAt); assertNull(catalog.error);
        assertFalse(CatalogPushResult.fail("no").ok);
        assertFalse(PluginSubmissionResult.fail("no").ok);
        assertNull(PluginSubmissionResult.success(null).submission);
    }

    @Test void approvedSubmissionNormalizesMetadataAndCopiesCollections() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(null, "bad"); values.put("missing", null); values.put(" ", "bad");
        values.put("empty", " "); values.put(" IRON ", " Scout ");
        var tiers = new ArrayList<>(List.of("iron"));
        var colours = new ArrayList<>(List.of("red"));
        var sub = new ApprovedSubmission("id", "uuid", "slug", "armor_set", " Norain ", "grip", "steel",
            tiers, Arrays.asList(null, " ", " IRON "), values, true, colours, List.of("bold"),
            List.of("file.png"), true, " category ", " scroll ", values, " custom ");
        tiers.clear(); colours.clear(); values.clear();
        assertEquals("id", sub.id); assertEquals("uuid", sub.playerUuid); assertEquals("grip", sub.gripPreset);
        assertEquals("steel", sub.baseSet); assertEquals(List.of("iron"), sub.tiers);
        assertEquals(List.of("red"), sub.nameColours); assertEquals(List.of("bold"), sub.nameStyles);
        assertEquals(List.of("file.png"), sub.files); assertEquals(Map.of("iron", "Scout"), sub.tierAliases);
        assertEquals(Map.of("iron", "Scout"), sub.tierScrolls); assertTrue(sub.addName);
        assertEquals("category", sub.category); assertEquals("scroll", sub.scroll);
        assertEquals("custom", sub.resolveNamespace());
        assertTrue(sub.isHelmet3dTier(" IRON ")); assertFalse(sub.isHelmet3dTier(null));
        assertFalse(sub.isHelmet3dTier(" ")); assertFalse(sub.isHelmet3dTier("steel"));
        assertEquals("Norain Scout", sub.displayNameForTier("iron"));
        assertEquals("Norain Steel", sub.displayNameForTier("steel"));
        assertEquals("Norain", sub.displayNameForTier(null));
        assertThrows(UnsupportedOperationException.class, () -> sub.tiers.add("steel"));
    }

    @Test void constructorsSupportLegacyArmorAndNamespaceFallbacks() {
        try (var paths = mockStatic(PackPaths.class)) {
            paths.when(PackPaths::playerNamespace).thenReturn("players_dev");
            var legacy = new ApprovedSubmission("id", null, " slug ", "armor_set", " ", null, " iron ",
                null, null, false, null, null, null);
            assertEquals(List.of("iron"), legacy.tiers);
            assertEquals("slug Iron", legacy.displayNameForTier("iron"));
            assertEquals("players_dev", legacy.resolveNamespace());
            var empty = new ApprovedSubmission(null, null, null, "handheld", null, null, null,
                List.of(), null, null, false, null, null, null);
            assertEquals("Iron", empty.displayNameForTier("iron")); assertEquals("", empty.displayNameForTier(""));
            var staff = new ApprovedSubmission(null, null, "slug", "armor_set", null, null, " ",
                null, null, null, false, null, null, null, true, " ", " ", null, " ");
            assertTrue(staff.tiers.isEmpty()); assertNull(staff.category); assertNull(staff.scroll);
            assertEquals(PackPaths.STAFF_NAMESPACE, staff.resolveNamespace());
            var plugin = new PluginSubmission("id", "uuid", "slug", "armor_set", "display", "approved",
                " iron ", null, false, "category", null);
            assertEquals(List.of("iron"), plugin.tiers); assertEquals("players_dev", plugin.resolveNamespace());
            assertEquals("uuid", plugin.playerUuid); assertEquals("approved", plugin.status);
            assertEquals("category", plugin.category); assertEquals("display", plugin.displayName);
            var staffPlugin = new PluginSubmission(null, null, null, "handheld", null, null, null,
                List.of(), true, null, " ");
            assertEquals(PackPaths.STAFF_NAMESPACE, staffPlugin.resolveNamespace());
            var explicit = new PluginSubmission(null, null, null, "armor_set", null, null, " ",
                List.of("steel"), false, null, " custom ");
            assertEquals("custom", explicit.resolveNamespace()); assertEquals(List.of("steel"), explicit.tiers);
        }
    }

    @Test void listRoutesParsePayloadsAndPreserveFields() {
        try (var gateway = mockStatic(GatewayClient.class)) {
            gateway.when(() -> GatewayClient.request("GET", "/skins/plugin/codes/active", null)).thenReturn(GatewayClient.Result.success("""
                {"codes":[{}, {"code":""}, {"code":"abc","player_uuid":"u","minecraft_name":"n","created_at":"c","expires_at":"e"}]}
                """));
            var code = listActiveCodes().codes.getFirst();
            assertEquals("abc", code.code); assertEquals("u", code.playerUuid);
            assertEquals("n", code.minecraftName); assertEquals("c", code.createdAt); assertEquals("e", code.expiresAt);
            gateway.when(() -> GatewayClient.request("GET", "/skins/plugin/approved", null)).thenReturn(GatewayClient.Result.success("""
                {"submissions":[{}, {"id":""}, {"id":"id","slug":"s","kind":"armor_set","tiers":["iron"],"tier_aliases":{"iron":"Scout"},"helmet_3d_tiers":["iron"],"add_name":true,"staff":true,"tier_scrolls":{"iron":"scroll"},"ia_namespace":"staff"}]}
                """));
            var sub = listApproved().submissions.getFirst();
            assertEquals("id", sub.id); assertEquals("s Scout", sub.displayNameForTier("iron"));
            assertTrue(sub.addName); assertTrue(sub.staff); assertTrue(sub.isHelmet3dTier("iron"));
            assertEquals("scroll", sub.tierScrolls.get("iron")); assertEquals("staff", sub.resolveNamespace());
            gateway.verify(() -> GatewayClient.request("GET", "/skins/plugin/approved", null));
        }
    }

    @Test void listRecordsAreParsedOnceWhileReusingAllTypedFields() {
        String submission = """
            {"id":"skin","player_uuid":"owner","slug":"blue","kind":"armor_set",
             "display_name":"Blue","grip_preset":"middle","base_set":"iron","tiers":["iron"],
             "helmet_3d_tiers":["iron"],"tier_aliases":{"iron":"Scout"},"add_name":true,
             "name_colours":["#123456"],"name_styles":["bold"],"files":["iron.png"],
             "staff":" true ","category":"armor","scroll":"common",
             "tier_scrolls":{"iron":"rare"},"ia_namespace":"curated"}
            """;
        try (var parser = mockStatic(JsonParser.class, CALLS_REAL_METHODS)) {
            var parsed = parseApprovedSubmissions("{\"submissions\":[{}," + submission + "]}");
            assertEquals(1, parsed.size()); var sub = parsed.getFirst();
            assertEquals("skin", sub.id); assertEquals("owner", sub.playerUuid); assertEquals("blue", sub.slug);
            assertEquals("Blue Scout", sub.displayNameForTier("iron")); assertEquals("middle", sub.gripPreset);
            assertTrue(sub.addName); assertTrue(sub.staff); assertTrue(sub.isHelmet3dTier("iron"));
            assertEquals(List.of("#123456"), sub.nameColours); assertEquals(List.of("bold"), sub.nameStyles);
            assertEquals(List.of("iron.png"), sub.files); assertEquals("armor", sub.category); assertEquals("common", sub.scroll);
            assertEquals(Map.of("iron", "rare"), sub.tierScrolls); assertEquals("curated", sub.resolveNamespace());
            parser.verify(() -> JsonParser.parseString(anyString()), times(2));
            parser.clearInvocations();
            var codes = parseActiveCodes("{\"codes\":[{}, {\"code\":\"abc\",\"player_uuid\":\"owner\",\"minecraft_name\":\"Name\",\"created_at\":\"start\",\"expires_at\":\"end\"}]}");
            assertEquals(1, codes.size()); assertEquals("abc", codes.getFirst().code); assertEquals("Name", codes.getFirst().minecraftName);
            assertEquals("start", codes.getFirst().createdAt); assertEquals("end", codes.getFirst().expiresAt);
            parser.verify(() -> JsonParser.parseString(anyString()), times(2));
        }
    }

    @Test void lookupAndCatalogResponsesAreParsedOnceEach() {
        String response = """
            {"id":"skin","player_uuid":"owner","slug":"blue","kind":"armor_set","display_name":"Blue",
             "status":"approved","base_set":"iron","tiers":["steel"],"staff":true,"category":"armor",
             "ia_namespace":"curated","categories":2,"skin_sets":3,"scrolls":4,"updated_at":"now"}
            """;
        try (var gateway = mockStatic(GatewayClient.class); var parser = mockStatic(JsonParser.class, CALLS_REAL_METHODS)) {
            gateway.when(() -> GatewayClient.request(anyString(), anyString(), nullable(String.class)))
                .thenReturn(GatewayClient.Result.success(response));
            var lookup = getSubmission("skin"); assertTrue(lookup.ok);
            assertEquals("owner", lookup.submission.playerUuid); assertEquals("Blue", lookup.submission.displayName);
            assertEquals("approved", lookup.submission.status); assertEquals(List.of("steel"), lookup.submission.tiers);
            assertTrue(lookup.submission.staff); assertEquals("armor", lookup.submission.category);
            assertEquals("curated", lookup.submission.resolveNamespace());
            parser.verify(() -> JsonParser.parseString(response), times(1));
            parser.clearInvocations();
            var catalog = pushCatalog("{}"); assertTrue(catalog.ok);
            assertEquals(2, catalog.categories); assertEquals(3, catalog.skinSets); assertEquals(4, catalog.scrolls);
            assertEquals("now", catalog.updatedAt); parser.verify(() -> JsonParser.parseString(response), times(1));
        }
    }

    @Test void typedReadersKeepRootOnlyFieldsAndTolerateMalformedResponses() {
        String nested = "{\"nested\":{\"id\":\"wrong\",\"x\":true,\"tiers\":[\"iron\"],\"aliases\":{\"iron\":\"Scout\"}}}";
        assertNull(jsonString(nested, "id")); assertFalse(jsonTruthy(nested, "x")); assertEquals(0, jsonInt(nested, "x"));
        assertTrue(jsonStringArray(nested, "tiers").isEmpty()); assertTrue(jsonStringMap(nested, "aliases").isEmpty());
        try (var gateway = mockStatic(GatewayClient.class)) {
            for (String malformed : Arrays.asList(null, "null", "[]", "\"scalar\"", "{\"id\":")) {
                gateway.when(() -> GatewayClient.request(anyString(), anyString(), nullable(String.class)))
                    .thenReturn(GatewayClient.Result.success(malformed));
                var lookup = getSubmission("skin"); assertFalse(lookup.ok);
                assertEquals("submission response missing id/slug", lookup.error);
                var catalog = pushCatalog("{}"); assertTrue(catalog.ok);
                assertEquals(0, catalog.categories); assertEquals(0, catalog.skinSets); assertEquals(0, catalog.scrolls);
                assertNull(catalog.updatedAt);
            }
        }
    }

    @Test void remoteFailuresReachEveryResultType() {
        try (var gateway = mockStatic(GatewayClient.class)) {
            gateway.when(() -> GatewayClient.request(anyString(), anyString(), nullable(String.class)))
                .thenReturn(GatewayClient.Result.fail("offline"));
            assertEquals("offline", listActiveCodes().error); assertEquals("offline", listApproved().error);
            assertEquals("offline", revokeCode("code").error); assertEquals("offline", revokeSubmission("id").error);
            assertEquals("offline", markApplied(List.of("id")).error); assertEquals("offline", pushCatalog("{}").error);
            assertEquals("offline", getSubmission("id").error); assertEquals("offline", listDeletableSubmissionIds().error);
            assertEquals("offline", listDeletableStaffSkinIds().error);
        }
    }

    @Test void mutationsSendTrimmedEscapedPayloadsAndCorrectVerbs() {
        try (var gateway = mockStatic(GatewayClient.class)) {
            gateway.when(() -> GatewayClient.request(anyString(), anyString(), nullable(String.class)))
                .thenReturn(GatewayClient.Result.success("{\"applied\":[\"a\",\"b\"],\"categories\":2,\"skin_sets\":3,\"scrolls\":4,\"updated_at\":\"now\"}"));
            assertTrue(revokeCode(" a\"b ").ok);
            gateway.verify(() -> GatewayClient.request("POST", "/skins/plugin/codes/revoke", "{\"code\":\"a\\\"b\"}"));
            assertTrue(revokeSubmission(" id ").ok);
            gateway.verify(() -> GatewayClient.request("POST", "/skins/plugin/submissions/id/revoke", "{}"));
            assertEquals(List.of("a", "b"), markApplied(Arrays.asList(null, " ", " a ", "b")).applied);
            gateway.verify(() -> GatewayClient.request("POST", "/skins/plugin/applied", "{\"submission_ids\":[\"a\",\"b\"]}"));
            var catalog = pushCatalog("{\"categories\":[]}");
            assertEquals(2, catalog.categories); assertEquals(3, catalog.skinSets);
            assertEquals(4, catalog.scrolls); assertEquals("now", catalog.updatedAt);
            gateway.verify(() -> GatewayClient.request("PUT", "/skins/plugin/catalog", "{\"categories\":[]}"));
        }
    }

    @Test void emptyMutationInputsDoNotContactGateway() {
        try (var gateway = mockStatic(GatewayClient.class)) {
            for (String empty : Arrays.asList(null, "", " ")) {
                assertFalse(revokeCode(empty).ok); assertFalse(revokeSubmission(empty).ok);
                assertFalse(getSubmission(empty).ok); assertFalse(pushCatalog(empty).ok);
            }
            assertTrue(markApplied(null).applied.isEmpty()); assertTrue(markApplied(List.of()).applied.isEmpty());
            assertTrue(markApplied(Arrays.asList(null, " ")).applied.isEmpty()); gateway.verifyNoInteractions();
        }
    }

    @Test void submissionLookupRejectsMissingIdentityAndReturnsRecord() {
        try (var gateway = mockStatic(GatewayClient.class)) {
            gateway.when(() -> GatewayClient.request("GET", "/skins/plugin/submissions/id", null))
                .thenReturn(GatewayClient.Result.success("{}"), GatewayClient.Result.success("{\"id\":\" \"}"),
                    GatewayClient.Result.success("{\"id\":\"id\"}"), GatewayClient.Result.success("{\"id\":\"id\",\"slug\":\" \"}"),
                    GatewayClient.Result.success("{\"id\":\"id\",\"slug\":\"skin\",\"kind\":\"armor_set\",\"base_set\":\"iron\",\"staff\":true,\"category\":\"hats\",\"ia_namespace\":\"custom\"}"));
            for (int i = 0; i < 4; i++) assertFalse(getSubmission("id").ok);
            var result = getSubmission(" id ");
            assertTrue(result.ok); assertEquals("skin", result.submission.slug);
            assertEquals(List.of("iron"), result.submission.tiers); assertEquals("custom", result.submission.resolveNamespace());
        }
    }

    @Test void deletableListsSkipMissingIdsAndTrimValidIds() {
        try (var gateway = mockStatic(GatewayClient.class)) {
            gateway.when(() -> GatewayClient.request("GET", "/skins/plugin/submissions/deletable", null))
                .thenReturn(GatewayClient.Result.success("{\"submissions\":[{}, {\"id\":\" \"}, {\"id\":\" one \"}]}"));
            assertEquals(List.of("one"), listDeletableSubmissionIds().ids);
            gateway.when(() -> GatewayClient.request("GET", "/skins/plugin/skins/deletable", null))
                .thenReturn(GatewayClient.Result.success("{}"), GatewayClient.Result.success("{\"skins\":[]}"), GatewayClient.Result.success("{\"skins\":[{\"id\":\"two\"}]}"));
            assertTrue(listDeletableStaffSkinIds().ids.isEmpty()); assertTrue(listDeletableStaffSkinIds().ids.isEmpty());
            assertEquals(List.of("two"), listDeletableStaffSkinIds().ids);
        }
    }

    @Test void downloadsValidateNamesEncodeSpacesAndHandleEmptyResponses() {
        try (var gateway = mockStatic(GatewayClient.class)) {
            assertFalse(downloadSubmissionFile(null, "file").ok); assertFalse(downloadSubmissionFile("id", null).ok);
            for (String name : List.of("", "..file", "a/b", "a\\b")) assertFalse(downloadSubmissionFile("id", name).ok);
            gateway.verifyNoInteractions();
            gateway.when(() -> GatewayClient.download("/skins/plugin/submissions/id/files/my%20file.png"))
                .thenReturn(GatewayClient.BytesDownload.fail("offline"), GatewayClient.BytesDownload.success(null),
                    GatewayClient.BytesDownload.success(new byte[0]), GatewayClient.BytesDownload.success(new byte[]{3}));
            assertEquals("offline", downloadSubmissionFile(" id ", " my file.png ").error);
            assertFalse(downloadSubmissionFile("id", "my file.png").ok); assertFalse(downloadSubmissionFile("id", "my file.png").ok);
            assertArrayEquals(new byte[]{3}, downloadSubmissionFile("id", "my file.png").data);
        }
    }

    @Test void absentAndMalformedJsonContainersAreIgnored() {
        for (String json : Arrays.asList(null, "", "{}", "{\"codes\":null}")) assertTrue(parseActiveCodes(json).isEmpty());
        for (String json : Arrays.asList(null, "", "{}", "{\"submissions\":null}")) assertTrue(parseApprovedSubmissions(json).isEmpty());
        assertNull(jsonArrayBody("{}", null)); assertNull(jsonObjectBody("{}", null)); assertNull(jsonString("{}", null));
        for (String malformed : Arrays.asList(null, "{}", "\"x\"", "\"x\":", "{\"x\":null}", "{\"x\":true}")) {
            assertNull(jsonArrayBody(malformed, "x")); assertNull(jsonObjectBody(malformed, "x"));
        }
        assertNull(jsonArrayBody("{\"x\": [1", "x")); assertNull(jsonObjectBody("{\"x\": {\"a\":1", "x"));
        assertTrue(splitJsonObjects(null).isEmpty()); assertTrue(jsonStringArray("{}", "x").isEmpty());
        assertTrue(jsonStringMap("{}", "x").isEmpty()); assertTrue(jsonStringMap("{\"x\":{}}", "x").isEmpty());
    }

    @Test void scanningPreservesNestedContentAndQuotedDelimiters() {
        String body = "[1], \"a]\\\"b\", {\"c\":2}";
        assertEquals(body, jsonArrayBody("{\"x\": [" + body + "]}", "x"));
        String object = "\"a\":{\"b\":\"}\\\"{\"}";
        assertEquals(object, jsonObjectBody("{\"x\": {" + object + "}}", "x"));
        String first = "{\"a\":{\"b\":\"}\\\"{\"}}";
        assertEquals(List.of(first, "{}"), splitJsonObjects(first + ", {}"));
        assertEquals(List.of("a\"b", "c\\d"), jsonStringArray("{\"x\":[\"a\\\"b\",\"c\\\\d\"]}", "x"));
        assertEquals(Map.of("iron", "A\"B", "steel", "C\\D"), jsonStringMap("{\"x\":{\" IRON \":\" A\\\"B \",\"steel\":\"C\\\\D\",\" \":\"skip\"}}", "x"));
    }

    @Test void primitiveFieldsSupportNullBooleanAndIntegerResults() {
        for (String json : Arrays.asList(null, "{}", "\"x\"", "\"x\": ", "{\"x\":null}")) assertNull(jsonString(json, "x"));
        assertEquals("quoted\"value", jsonString("{\"x\": \"quoted\\\"value\"}", "x"));
        assertTrue(jsonTruthy("{\"x\":true}", "x")); assertTrue(jsonTruthy("{\"x\":\" TRUE \"}", "x"));
        assertFalse(jsonTruthy("{}", "x")); assertFalse(jsonTruthy("{\"x\":false}", "x"));
        assertEquals(12, jsonInt("{\"x\":12,\"other\":1}", "x"));
        assertEquals(0, jsonInt("{}", "x")); assertEquals(0, jsonInt("{\"x\":\" \"}", "x"));
        assertEquals(0, jsonInt("{\"x\":\"overflow\"}", "x"));
        assertEquals("", escapeJson(null)); assertEquals("a\\\"\\\\\\n\\r\\t", escapeJson("a\"\\\n\r\t"));
    }

    @Test void jsonStringEscapesDecodeToActualCharacters() {
        assertEquals("a\n\t\r\b\f/é", jsonString("{\"x\":\"a\\n\\t\\r\\b\\f\\/\\u00e9\"}", "x"));
    }
    @Test void jsonArrayEscapesDecodeToActualCharacters() {
        assertEquals(List.of("a\nb", "é"), jsonStringArray("{\"x\":[\"a\\nb\",\"\\u00e9\"]}", "x"));
    }
    @Test void jsonMapEscapesDecodeToActualCharacters() {
        assertEquals(Map.of("iron", "a\nbé"), jsonStringMap("{\"x\":{\"IRON\":\"a\\nb\\u00e9\"}}", "x"));
    }
    @Test void outgoingJsonEscapesAllControlCharacters() {
        String raw = "before\b\f\u0001after";
        String escaped = escapeJson(raw);
        assertEquals(raw, JsonParser.parseString("\"" + escaped + "\"").getAsString());
        for (char c : escaped.toCharArray()) assertTrue(c >= 0x20, "JSON contains an unescaped control character");
    }
}
