package com.zoner.reminder;

import com.zoner.auth.CurrentUser;
import com.zoner.auth.UserPrincipal;
import com.zoner.reminder.NotificationDto.NotificationResponse;
import com.zoner.reminder.NotificationDto.UnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app notifications and reminder alerts")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List recent notifications for the authenticated user")
    public ResponseEntity<List<NotificationResponse>> listNotifications(
            @CurrentUser UserPrincipal principal,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        List<NotificationResponse> list = notificationService.listNotifications(principal.getId(), limit);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get the number of unread notifications")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @CurrentUser UserPrincipal principal) {
        UnreadCountResponse response = notificationService.getUnreadCount(principal.getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id) {
        NotificationResponse response = notificationService.markAsRead(principal.getId(), id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mark-all-read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark all notifications as read for the authenticated user")
    public void markAllAsRead(@CurrentUser UserPrincipal principal) {
        notificationService.markAllAsRead(principal.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a notification")
    public void deleteNotification(
            @CurrentUser UserPrincipal principal,
            @PathVariable Long id) {
        notificationService.deleteNotification(principal.getId(), id);
    }
}
