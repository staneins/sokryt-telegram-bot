package com.kaminsky;

import com.kaminsky.config.BotConfig;
import com.kaminsky.service.AdminService;
import com.kaminsky.service.CommandHandler;
import com.kaminsky.service.MessageService;
import com.kaminsky.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import com.kaminsky.finals.BotFinalVariables;
import org.telegram.telegrambots.meta.api.objects.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommandHandlerTest {

    @InjectMocks
    private CommandHandler commandHandler;

    @Mock
    private AdminService adminService;

    @Mock
    private UserService userService;

    @Mock
    private MessageService messageService;

    @Mock
    private BotConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testHandleMessage_NoText() {
        Message message = mock(Message.class);
        when(message.hasText()).thenReturn(false);

        CommandHandler spyHandler = Mockito.spy(commandHandler);
        spyHandler.handleMessage(message);

        verify(message, times(1)).hasText();
        verify(spyHandler, never()).handleCommand(any(Message.class));
        verify(spyHandler, never()).handleNonCommandMessage(any(Message.class));
    }

    @Test
    void testHandleMessage_CommandMessage() {
        Message message = mock(Message.class);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/start");
        when(message.getChat().isGroupChat()).thenReturn(false);
        when(message.getChat().isUserChat()).thenReturn(true);
        when(message.getFrom()).thenReturn(mock(User.class));
        when(message.getChat()).thenReturn(mock(Chat.class));
        when(message.getFrom().getId()).thenReturn(1L);

        CommandHandler spyHandler = Mockito.spy(commandHandler);

        spyHandler.handleMessage(message);

        verify(message, times(1)).hasText();
        verify(message, times(2)).getText();
        verify(spyHandler, times(1)).handleCommand(message);
        verify(spyHandler, never()).handleNonCommandMessage(any(Message.class));
    }

    @Test
    void testHandleMessage_NonCommandMessage() {
        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(mock(User.class));
        when(message.getChat()).thenReturn(mock(Chat.class));
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("Привет!");

        CommandHandler spyHandler = Mockito.spy(commandHandler);

        spyHandler.handleMessage(message);

        verify(message, times(1)).hasText();
        verify(message, times(2)).getText();
        verify(spyHandler, never()).handleCommand(any(Message.class));
        verify(spyHandler, times(1)).handleNonCommandMessage(message);
    }

    @Test
    void testHandleCommand_StartCommand() {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn("/start");
        when(message.getChatId()).thenReturn(12345L);
        when(message.getFrom()).thenReturn(mock(User.class));
        when(message.getChat()).thenReturn(mock(Chat.class));
        when(message.getChat().isGroupChat()).thenReturn(false);
        when(message.getChat().isUserChat()).thenReturn(true);
        when(message.getChat().getFirstName()).thenReturn("John");

        commandHandler.handleCommand(message);

        verify(adminService, times(1)).registerUser(message);
        verify(messageService, times(1)).startCommandReceived(12345L, "John");
    }

    @Test
    void testHandleCommand_HelpCommand() {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn("/help");
        when(message.getChatId()).thenReturn(12345L);
        when(message.getFrom()).thenReturn(mock(User.class));
        when(message.getChat()).thenReturn(mock(Chat.class));
        when(message.getChat().isGroupChat()).thenReturn(false);
        when(message.getChat().isUserChat()).thenReturn(true);

        commandHandler.handleCommand(message);

        verify(messageService, times(1)).sendMessage(12345L, BotFinalVariables.HELP_TEXT);
    }

    @Test
    void testHandleCommand_ConfigCommand() {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn("/config");
        when(message.getChatId()).thenReturn(12345L);
        when(message.getFrom()).thenReturn(mock(User.class));
        when(message.getChat()).thenReturn(mock(Chat.class));
        when(message.getChat().isGroupChat()).thenReturn(false);
        when(message.getChat().isUserChat()).thenReturn(true);

        commandHandler.handleCommand(message);

        verify(adminService, times(1)).handleConfigCommand(12345L, message);
    }

    @Test
    void testHandleCommand_UnknownCommand_NotHandled() {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn("/unknown");
        when(message.getChatId()).thenReturn(12345L);
        when(message.getFrom()).thenReturn(mock(User.class));
        when(message.getChat()).thenReturn(mock(Chat.class));
        when(message.getChat().isGroupChat()).thenReturn(false);
        when(message.getChat().isUserChat()).thenReturn(true);

        when(userService.isCommandHandled()).thenReturn(false);

        commandHandler.handleCommand(message);

        verify(userService, times(1)).isCommandHandled();
        verify(messageService, times(1)).sendMessage(12345L, BotFinalVariables.UNKNOWN_COMMAND);
        verify(userService, never()).setCommandHandled(false);
    }

    @Test
    void testHandleCommand_UnknownCommand_Handled() {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn("/unknown");
        when(message.getChatId()).thenReturn(12345L);
        when(message.getFrom()).thenReturn(mock(User.class));
        when(message.getChat()).thenReturn(mock(Chat.class));
        when(message.getChat().isGroupChat()).thenReturn(false);
        when(message.getChat().isUserChat()).thenReturn(true);

        when(userService.isCommandHandled()).thenReturn(true);

        commandHandler.handleCommand(message);

        verify(userService, times(1)).isCommandHandled();
        verify(messageService, never()).sendMessage(anyLong(), anyString());
        verify(userService, times(1)).setCommandHandled(false);
    }

    @Test
    void testHandleCommand_SendCommand_NotOwner() {
        Message message = mock(Message.class);
        when(message.getText()).thenReturn("/send Привет всем!");
        when(message.getChatId()).thenReturn(12345L);
        when(message.getFrom()).thenReturn(mock(User.class));
        when(message.getChat()).thenReturn(mock(Chat.class));
        User fromUser = message.getFrom();
        when(fromUser.getId()).thenReturn(2L);
        when(message.getChat().isGroupChat()).thenReturn(false);
        when(message.getChat().isUserChat()).thenReturn(true);

        Long ownerId = 1L;
        when(config.getOwnerId()).thenReturn(ownerId);

        commandHandler.handleCommand(message);

        verify(config, times(1)).getOwnerId();
        verify(userService, never()).sendToAllUsers(anyString());
        verify(messageService, times(1)).sendMessage(eq(12345L), anyString());
    }

    @Test
    void testHandleCommand_BanCommand() {
        Long chatId = 12345L;
        Long userId = 1L;
        String text = "/ban@sokrytbot";
        String objectName = "John";
        Long objectId = 2L;
        Message message = mock(Message.class);
        when(message.getFrom()).thenReturn(mock(User.class));
        when(message.getReplyToMessage()).thenReturn(mock(Message.class));
        when(message.getReplyToMessage().getFrom()).thenReturn(mock(User.class));
        when(message.getReplyToMessage().getFrom().getId()).thenReturn(objectId);
        when(message.getReplyToMessage().getFrom().getFirstName()).thenReturn(objectName);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getFrom().getId()).thenReturn(userId);
        when(message.getChat()).thenReturn(mock(Chat.class));
        when(message.getChat().isGroupChat()).thenReturn(true);
        when(message.getChat().isUserChat()).thenReturn(false);

        commandHandler.handleCommand(message);

        verify(adminService, times(1)).handleAdminCommandWithReply(chatId, userId, text, message);
    }

}