package com.kaminsky;

import com.kaminsky.service.ChatAdminService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ChatAdminServiceTest {

    @InjectMocks
    private ChatAdminService chatAdminService;

    @Test
    public void testIsAdmin_UserIsAdmin() {
        chatAdminService.putIntoChatAdministrators(1L, Arrays.asList(2L, 3L));

        boolean isAdmin = chatAdminService.isAdmin(2L);

        assertTrue(isAdmin);
    }

    @Test
    public void testIsAdmin_UserIsNotAdmin() {
        chatAdminService.putIntoChatAdministrators(1L, Arrays.asList(2L, 3L));

        boolean isAdmin = chatAdminService.isAdmin(4L);

        assertFalse(isAdmin);
    }
}
