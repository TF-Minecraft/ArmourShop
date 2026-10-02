package net.tfminecraft.armourshop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.tfminecraft.armourshop.api.GatewayClient;
import net.tfminecraft.armourshop.pack.shop.LuckPermsGrant;
import net.tfminecraft.armourshop.utils.*;
import net.tfminecraft.tfmcweb.api.ProvinceSystemGateway;
import net.luckperms.api.*;
import net.luckperms.api.model.user.*;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.node.Node;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RuntimeServicesTest {
    public static class Reply {public boolean ok;public Object body,error;public byte[] data;}
    @Test void gatewayTranslatesResponsesAndTransportFailures(){
        Object previous=ProvinceSystemGateway.result;RuntimeException previousFailure=ProvinceSystemGateway.failure;
        try{
            Reply r=new Reply();ProvinceSystemGateway.result=r;ProvinceSystemGateway.failure=null;
            assertEquals("request failed",GatewayClient.request("GET","/test",null).error);
            r.error="denied";assertEquals("denied",GatewayClient.requestBytes("POST","/test",new byte[0],"binary").error);
            assertEquals("denied",GatewayClient.download("/test").error);r.error=null;assertEquals("download failed",GatewayClient.download("/test").error);
            r.ok=true;r.body="payload";r.data=new byte[]{1,2};assertEquals("payload",GatewayClient.request("GET","/test",null).body);assertArrayEquals(r.data,GatewayClient.download("/test").data);
            r.body=null;assertEquals("",GatewayClient.requestBytes("POST","/test",new byte[0],"binary").body);
            assertEquals("",GatewayClient.Result.success(null).body);
            ProvinceSystemGateway.failure=new IllegalStateException("offline");assertTrue(GatewayClient.request("GET","/test",null).error.endsWith("offline"));assertTrue(GatewayClient.requestBytes("POST","/test",null,"binary").error.endsWith("offline"));assertTrue(GatewayClient.download("/test").error.endsWith("offline"));
            ProvinceSystemGateway.failure=new IllegalArgumentException();assertTrue(GatewayClient.request("GET","/test",null).error.endsWith("IllegalArgumentException"));
            ProvinceSystemGateway.failure=null;ProvinceSystemGateway.result=new Object();assertTrue(GatewayClient.request("GET","/test",null).error.contains("ok"));
        }finally{ProvinceSystemGateway.result=previous;ProvinceSystemGateway.failure=previousFailure;}
    }
    @Test void luckPermsGrantAndRevokeValidateInputsAndPersistNodes(){
        UUID uuid=UUID.randomUUID();Logger logger=mock(Logger.class);PluginManager manager=mock(PluginManager.class);Plugin plugin=mock(Plugin.class);
        LuckPerms api=mock(LuckPerms.class);UserManager users=mock(UserManager.class);User user=mock(User.class);NodeMap data=mock(NodeMap.class);
        when(api.getUserManager()).thenReturn(users);when(user.data()).thenReturn(data);
        var registry=mock(net.luckperms.api.node.NodeBuilderRegistry.class);
        var builder=mock(net.luckperms.api.node.types.PermissionNode.Builder.class,RETURNS_SELF);
        var node=mock(net.luckperms.api.node.types.PermissionNode.class);
        when(api.getNodeBuilderRegistry()).thenReturn(registry);when(registry.forPermission()).thenReturn(builder);
        when(builder.build()).thenReturn(node);when(node.getKey()).thenReturn("armourshop.submission.slug");when(node.getValue()).thenReturn(true);
        try(var bukkit=mockStatic(Bukkit.class);var provider=mockStatic(LuckPermsProvider.class)){
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);provider.when(LuckPermsProvider::get).thenReturn(api);
            for(boolean grant:List.of(true,false)){
                for(Logger log:Arrays.asList(logger,null)){
                    assertFalse(change(grant,null,"slug",log));assertFalse(change(grant,uuid,null,log));assertFalse(change(grant,uuid," ",log));
                    when(manager.getPlugin("LuckPerms")).thenReturn(null);assertFalse(change(grant,uuid,"slug",log));
                    when(manager.getPlugin("LuckPerms")).thenReturn(plugin);when(plugin.isEnabled()).thenReturn(false);assertFalse(change(grant,uuid,"slug",log));
                    when(plugin.isEnabled()).thenReturn(true);when(users.loadUser(uuid)).thenReturn(CompletableFuture.completedFuture(null));assertFalse(change(grant,uuid,"slug",log));
                    when(users.loadUser(uuid)).thenReturn(CompletableFuture.completedFuture(user));when(users.saveUser(user)).thenReturn(CompletableFuture.completedFuture(null));assertTrue(change(grant,uuid," slug ",log));
                    provider.when(LuckPermsProvider::get).thenThrow(new IllegalStateException("not ready"));assertFalse(change(grant,uuid,"slug",log));provider.when(LuckPermsProvider::get).thenReturn(api);
                    when(users.saveUser(user)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("save failed")));assertFalse(change(grant,uuid,"slug",log));
                }
            }
            verify(builder,atLeastOnce()).permission("armourshop.submission.slug");verify(builder,atLeastOnce()).value(true);
            ArgumentCaptor<Node> nodes=ArgumentCaptor.forClass(Node.class);verify(data,atLeastOnce()).add(nodes.capture());assertTrue(nodes.getAllValues().stream().allMatch(n->n.getKey().equals("armourshop.submission.slug")&&n.getValue()));verify(data,atLeastOnce()).remove(any(Node.class));
        }
    }
    boolean change(boolean grant,UUID uuid,String slug,Logger logger){return grant?LuckPermsGrant.grantSubmission(uuid,slug,logger):LuckPermsGrant.revokeSubmission(uuid,slug,logger);}
    @Test void chatMessagesProvideCopyAndDeleteActionsWithoutEmptyCodes(){
        Player p=mock(Player.class);ChatMessages.info(p,"info");ChatMessages.error(p,"error");verify(p).sendMessage(ChatMessages.PREFIX+"info");verify(p).sendMessage(ChatMessages.PREFIX+"§cerror");
        ChatMessages.sendCopyableCode(p,null,null);ChatMessages.sendCopyableCode(p,"","");ChatMessages.sendCopyableCode(p,"intro","ABC");verify(p).sendMessage(ChatMessages.PREFIX+"intro");
        ArgumentCaptor<Component> components=ArgumentCaptor.forClass(Component.class);verify(p).sendMessage(components.capture());Component code=components.getValue().children().get(1);assertEquals(ClickEvent.copyToClipboard("ABC"),code.clickEvent());
        clearInvocations(p);ChatMessages.sendTokenListLine(p,null,"owner");ChatMessages.sendTokenListLine(p,"","owner");verifyNoInteractions(p);
        for(String owner:Arrays.asList(null," ","  Ada  "))ChatMessages.sendTokenListLine(p,"ABC",owner);
        verify(p,times(3)).sendMessage(components.capture());Component delete=components.getValue().children().get(4);assertEquals(ClickEvent.runCommand("/armourshop token delete ABC"),delete.clickEvent());
        assertNotNull(new Cache());assertNotNull(new Permissions());
    }
}
