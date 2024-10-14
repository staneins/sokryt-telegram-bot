package com.kaminsky;

import com.kaminsky.config.BotConfig;
import com.kaminsky.finals.BotFinalVariables;
import com.kaminsky.model.repositories.UserRepository;
import com.kaminsky.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

class AdminServiceTest {

    @InjectMocks
    private AdminService adminService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageService messageService;

    @Mock
    private ChatAdminService chatAdminService;

    @Mock
    private UserService userService;

    @Mock
    private SchedulerService schedulerService;

    @Mock
    private BotConfig botConfig;

    @Mock
    private User fromUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegisterUser_NewUser() {
        Message message = mock(Message.class);
        User fromUser = mock(User.class);

        when(message.getFrom()).thenReturn(fromUser);
        when(fromUser.getId()).thenReturn(1L);
        when(fromUser.getUserName()).thenReturn("john_doe");
        when(fromUser.getFirstName()).thenReturn("John");
        when(fromUser.getLastName()).thenReturn("Doe");
        when(message.getChat()).thenReturn(mock(Chat.class));

        when(userService.getUserFromCache(1L)).thenReturn(Optional.empty());

        com.kaminsky.model.User newUser = new com.kaminsky.model.User();
        newUser.setChatId(1L);
        newUser.setUserName("john_doe");
        newUser.setFirstName("John");
        newUser.setLastName("Doe");

        when(userService.updateUserCache(any(com.kaminsky.model.User.class))).thenReturn(newUser);

        adminService.registerUser(message);

        verify(userService, times(1)).getUserFromCache(1L);
        verify(userService, times(1)).updateUserCache(any(com.kaminsky.model.User.class));
        verify(messageService, never()).sendMessage(anyLong(), anyString(), anyInt());
    }

    @Test
    void testRegisterUser_ExistingUser_NoUpdate() {
        Message message = mock(Message.class);
        User fromUser = mock(User.class);
        Chat chat = mock(Chat.class);

        when(message.getFrom()).thenReturn(fromUser);
        when(fromUser.getId()).thenReturn(2L);
        when(fromUser.getUserName()).thenReturn("jane_doe");
        when(message.getChat()).thenReturn(chat);

        com.kaminsky.model.User cachedUser = new com.kaminsky.model.User();
        cachedUser.setChatId(2L);
        cachedUser.setUserName("jane_doe");
        cachedUser.setFirstName("Jane");
        cachedUser.setLastName("Doe");
        cachedUser.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

        when(userService.getUserFromCache(2L)).thenReturn(Optional.of(cachedUser));

        adminService.registerUser(message);

        verify(userService, times(1)).getUserFromCache(2L);
        verify(userService, never()).updateUserCache(any(com.kaminsky.model.User.class));
        verify(messageService, never()).sendMessage(anyLong(), anyString(), anyInt());
    }

    @Test
    void testBanUser_SuccessfulBan() {
        Long chatId = 100L;
        Long commandSenderId = 1L;
        Long bannedUserId = 2L;
        String bannedUserNickname = "bad_user";

        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(fromUser);
        when(message.getFrom().getId()).thenReturn(commandSenderId);

        when(chatAdminService.isAdmin(chatId, commandSenderId)).thenReturn(true);
        when(chatAdminService.isAdmin(chatId, bannedUserId)).thenReturn(false);
        when(userService.isUserBanned(bannedUserId)).thenReturn(false);

        com.kaminsky.model.User bannedUser = new com.kaminsky.model.User();
        bannedUser.setChatId(bannedUserId);
        bannedUser.setUserName("bad_user");

        when(adminService.getOrRegisterWarnedUser(message, bannedUserId)).thenReturn(bannedUser);

        adminService.banUser(chatId, commandSenderId, bannedUserId, bannedUserNickname, message);

        verify(chatAdminService, times(1)).isAdmin(chatId, commandSenderId);
        verify(chatAdminService, times(1)).isAdmin(chatId, bannedUserId);
        verify(adminService, times(1)).getOrRegisterWarnedUser(message, bannedUserId);
        verify(userService, times(1)).addBannedUser(bannedUserId);
        verify(schedulerService, times(1)).startBannedUsersCleanupTask(1, TimeUnit.MINUTES);
        verify(messageService, times(1)).executeBanChatMember(any(BanChatMember.class));
        verify(messageService, times(1)).sendHTMLMessage(eq(chatId), anyString(), eq(message.getMessageId()));
    }

    @Test
    void testBanUser_AttemptToBanAdmin() {
        Long chatId = 100L;
        Long commandSenderId = 1L;
        Long bannedUserId = 2L;
        String bannedUserNickname = "admin";

        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(mock(User.class));
        when(message.getFrom().getId()).thenReturn(commandSenderId);

        AdminService spyAdminService = Mockito.spy(adminService);

        when(chatAdminService.isAdmin(chatId, commandSenderId)).thenReturn(true);
        when(chatAdminService.isAdmin(chatId, bannedUserId)).thenReturn(true);

        // Act
        spyAdminService.banUser(chatId, commandSenderId, bannedUserId, bannedUserNickname, message);

        // Assert
        verify(chatAdminService, times(1)).isAdmin(chatId, commandSenderId);
        verify(chatAdminService, times(1)).isAdmin(chatId, bannedUserId);
        verify(messageService, times(1)).sendMessage(chatId, "Не могу забанить администратора.");
        verify(spyAdminService, never()).getOrRegisterWarnedUser(any(), anyLong());
    }

    @Test
    void testBanUser_AlreadyBanned() {
        Long chatId = 100L;
        Long commandSenderId = 1L;
        Long bannedUserId = 2L;
        String bannedUserNickname = "already_banned_user";

        Message message = mock(Message.class);
        User sender = mock(User.class);
        when(message.getFrom()).thenReturn(sender);
        when(sender.getId()).thenReturn(commandSenderId);

        AdminService spyAdminService = Mockito.spy(adminService);
        when(chatAdminService.isAdmin(chatId, commandSenderId)).thenReturn(true);
        when(spyAdminService.isUserAlreadyBanned(bannedUserId, chatId)).thenReturn(true);

        spyAdminService.banUser(chatId, commandSenderId, bannedUserId, bannedUserNickname, message);

        verify(chatAdminService, times(1)).isAdmin(chatId, commandSenderId);
        verify(spyAdminService, times(1)).isUserAlreadyBanned(bannedUserId, chatId);
        verify(messageService, times(1)).sendMessage(chatId, BotFinalVariables.USER_BANNED);
        verify(spyAdminService, never()).getOrRegisterWarnedUser(any(), anyLong());
    }


    @Test
    void testWarnUser_FirstWarning() {
        Long chatId = 100L;
        Long commandSenderId = 1L;
        Long warnedUserId = 2L;
        String warnedUserNickname = "good_user";
        int messageId = 123;

        Message message = mock(Message.class);
        User sender = mock(User.class);
        when(message.getFrom()).thenReturn(sender);
        when(sender.getId()).thenReturn(commandSenderId);
        when(message.getMessageId()).thenReturn(messageId);

        AdminService spyAdminService = Mockito.spy(adminService);

        when(chatAdminService.isAdmin(chatId, commandSenderId)).thenReturn(true);
        when(spyAdminService.isUserAlreadyBanned(warnedUserId, chatId)).thenReturn(false);

        com.kaminsky.model.User warnedUser = new com.kaminsky.model.User();
        warnedUser.setChatId(warnedUserId);
        warnedUser.setUserName("good_user");
        warnedUser.setNumberOfWarns(null);

        when(spyAdminService.getOrRegisterWarnedUser(message, warnedUserId)).thenReturn(warnedUser);
        when(userRepository.save(any(com.kaminsky.model.User.class))).thenReturn(warnedUser);

        spyAdminService.warnUser(chatId, commandSenderId, warnedUserId, warnedUserNickname, message);

        verify(chatAdminService, times(1)).isAdmin(chatId, commandSenderId);
        verify(spyAdminService, times(1)).isUserAlreadyBanned(warnedUserId, chatId);
        verify(spyAdminService, times(1)).getOrRegisterWarnedUser(message, warnedUserId);
        verify(userRepository, times(1)).save(warnedUser);
        verify(messageService, times(1)).sendHTMLMessage(eq(chatId), contains("предупрежден"), eq(messageId));
        verify(userService, never()).addBannedUser(anyLong());
    }

    @Test
    void testWarnUser_SecondWarning() {
        Long chatId = 100L;
        Long commandSenderId = 1L;
        Long warnedUserId = 2L;
        String warnedUserNickname = "good_user";
        int messageId = 123;

        Message message = mock(Message.class);
        User sender = mock(User.class);
        when(message.getFrom()).thenReturn(sender);
        when(sender.getId()).thenReturn(commandSenderId);
        when(message.getMessageId()).thenReturn(messageId);

        AdminService spyAdminService = Mockito.spy(adminService);

        when(chatAdminService.isAdmin(chatId, commandSenderId)).thenReturn(true);
        when(spyAdminService.isUserAlreadyBanned(warnedUserId, chatId)).thenReturn(false);

        com.kaminsky.model.User warnedUser = new com.kaminsky.model.User();
        warnedUser.setChatId(warnedUserId);
        warnedUser.setUserName("good_user");
        warnedUser.setNumberOfWarns((byte) 1);

        when(spyAdminService.getOrRegisterWarnedUser(message, warnedUserId)).thenReturn(warnedUser);
        when(userRepository.save(any(com.kaminsky.model.User.class))).thenReturn(warnedUser);

        spyAdminService.warnUser(chatId, commandSenderId, warnedUserId, warnedUserNickname, message);

        verify(chatAdminService, times(1)).isAdmin(chatId, commandSenderId);
        verify(spyAdminService, times(1)).isUserAlreadyBanned(warnedUserId, chatId);
        verify(spyAdminService, times(1)).getOrRegisterWarnedUser(message, warnedUserId);
        verify(userRepository, times(1)).save(warnedUser);
        verify(messageService, times(1)).sendHTMLMessage(eq(chatId), contains("2"), eq(messageId));
        verify(userService, never()).addBannedUser(anyLong());
    }

    @Test
    void testWarnUser_ThirdWarning_Ban() {
        Long chatId = 100L;
        Long commandSenderId = 1L;
        Long warnedUserId = 2L;
        Integer messageId = 123;
        String warnedUserNickname = "good_user";

        Message message = mock(Message.class);
        User sender = mock(User.class);
        when(message.getFrom()).thenReturn(sender);
        when(message.getMessageId()).thenReturn(messageId);
        when(sender.getId()).thenReturn(commandSenderId);
        when(message.getMessageId()).thenReturn(messageId);

        AdminService spyAdminService = Mockito.spy(adminService);

        when(chatAdminService.isAdmin(chatId, commandSenderId)).thenReturn(true);
        when(spyAdminService.isUserAlreadyBanned(warnedUserId, chatId)).thenReturn(false);

        com.kaminsky.model.User warnedUser = new com.kaminsky.model.User();
        warnedUser.setChatId(warnedUserId);
        warnedUser.setUserName("good_user");
        warnedUser.setNumberOfWarns((byte) 2);

        when(spyAdminService.getOrRegisterWarnedUser(message, warnedUserId)).thenReturn(warnedUser);
        when(userRepository.save(any(com.kaminsky.model.User.class))).thenReturn(warnedUser);

        spyAdminService.warnUser(chatId, commandSenderId, warnedUserId, warnedUserNickname, message);

        verify(chatAdminService, times(2)).isAdmin(chatId, commandSenderId);
        verify(spyAdminService, times(2)).isUserAlreadyBanned(warnedUserId, chatId);
        verify(spyAdminService, times(1)).getOrRegisterWarnedUser(message, warnedUserId);
        verify(userRepository, times(1)).save(warnedUser);
        verify(spyAdminService, times(1)).banUser(chatId, commandSenderId, warnedUserId, warnedUserNickname, message);
        verify(messageService, times(1)).sendHTMLMessage(eq(chatId), contains("уничтожен"), eq(messageId));
        verify(userService, times(1)).addBannedUser(warnedUserId);
        verify(schedulerService, times(1)).startBannedUsersCleanupTask(1, TimeUnit.MINUTES);
    }

    @Test
    void testMuteUser_SuccessfulMute() {
        Long chatId = 100L;
        Long commandSenderId = 1L;
        Long warnedUserId = 2L;
        String warnedUserNickname = "bad_user";
        Integer messageId = 123;

        Message message = mock(Message.class);
        User sender = mock(User.class);
        when(message.getFrom()).thenReturn(sender);
        when(sender.getId()).thenReturn(commandSenderId);
        when(message.getMessageId()).thenReturn(messageId);

        AdminService spyAdminService = Mockito.spy(adminService);

        when(chatAdminService.isAdmin(chatId, commandSenderId)).thenReturn(true);
        when(chatAdminService.isAdmin(chatId, warnedUserId)).thenReturn(false);
        when(userService.isUserBanned(warnedUserId)).thenReturn(false);

        com.kaminsky.model.User warnedUser = new com.kaminsky.model.User();
        warnedUser.setChatId(warnedUserId);
        warnedUser.setUserName("bad_user");

        doReturn(warnedUser).when(spyAdminService).getOrRegisterWarnedUser(message, warnedUserId);

        when(userRepository.save(any(com.kaminsky.model.User.class))).thenReturn(warnedUser);

        spyAdminService.muteUser(chatId, warnedUserId, warnedUserNickname, message);

        verify(chatAdminService, times(1)).isAdmin(chatId, commandSenderId);
        verify(chatAdminService, times(1)).isAdmin(chatId, warnedUserId);
        verify(userService, never()).addBannedUser(warnedUserId);
        verify(messageService, never()).executeBanChatMember(any(BanChatMember.class));
        verify(spyAdminService, times(1)).isUserAlreadyBanned(warnedUserId, chatId);
        verify(messageService, times(1)).executeRestrictChatMember(any(RestrictChatMember.class));
        verify(messageService, times(1)).sendHTMLMessageWithKeyboard(eq(chatId), contains("обеззвучен"), any(InlineKeyboardMarkup.class), eq(messageId));
    }

    @Test
    void testUnmuteUser_SuccessfulUnmute() {
        Long chatId = 100L;
        Long commandSenderId = 1L;
        Long warnedUserId = 2L;
        String warnedUserNickname = "bad_user";
        Integer messageId = 123;

        Message message = mock(Message.class);
        User sender = mock(User.class);
        when(message.getFrom()).thenReturn(sender);
        when(sender.getId()).thenReturn(commandSenderId);
        when(message.getMessageId()).thenReturn(messageId);

        AdminService spyAdminService = Mockito.spy(adminService);

        when(chatAdminService.isAdmin(chatId, commandSenderId)).thenReturn(true);
        when(chatAdminService.isAdmin(chatId, warnedUserId)).thenReturn(false);
        when(userService.isUserBanned(warnedUserId)).thenReturn(false);
        when(spyAdminService.isUserAlreadyBanned(warnedUserId, chatId)).thenReturn(false); // Ensure it returns false

        com.kaminsky.model.User warnedUser = new com.kaminsky.model.User();
        warnedUser.setChatId(warnedUserId);
        warnedUser.setUserName("bad_user");

        doReturn(warnedUser).when(spyAdminService).getOrRegisterWarnedUser(message, warnedUserId);
        when(userRepository.save(any(com.kaminsky.model.User.class))).thenReturn(warnedUser);

        spyAdminService.unmuteUser(chatId, warnedUserId, warnedUserNickname, message);

        verify(chatAdminService, times(1)).isAdmin(chatId, commandSenderId);
        verify(userService, never()).addBannedUser(warnedUserId);
        verify(messageService, never()).executeBanChatMember(any(BanChatMember.class));
        verify(spyAdminService, times(1)).isUserAlreadyBanned(warnedUserId, chatId);
        verify(messageService, times(1)).executeRestrictChatMember(any(RestrictChatMember.class));
        verify(messageService, times(1)).executeRestrictChatMember(any(RestrictChatMember.class));
        verify(messageService, times(1)).sendHTMLMessage(eq(chatId), eq("Все ограничения сняты с пользователя <a href=\"tg://user?id=2\">bad_user</a>"));
        verify(messageService, times(1)).executeDeleteMessage(any(DeleteMessage.class));
    }
}

