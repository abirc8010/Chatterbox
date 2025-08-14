package com.chatterbox.chat.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_one_id", nullable = false)
    private User userOne;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_two_id", nullable = false)
    private User userTwo;

    @jakarta.persistence.Column
    private Integer userOneUnread = 0;

    @jakarta.persistence.Column
    private Integer userTwoUnread = 0;

    public Conversation() { }

    public UUID getId() { return id; }
    public User getUserOne() { return userOne; }
    public void setUserOne(User userOne) { this.userOne = userOne; }
    public User getUserTwo() { return userTwo; }
    public void setUserTwo(User userTwo) { this.userTwo = userTwo; }
    public int getUserOneUnread() { return userOneUnread == null ? 0 : userOneUnread; }
    public void setUserOneUnread(int userOneUnread) { this.userOneUnread = userOneUnread; }
    public int getUserTwoUnread() { return userTwoUnread == null ? 0 : userTwoUnread; }
    public void setUserTwoUnread(int userTwoUnread) { this.userTwoUnread = userTwoUnread; }

    public void incrementUnreadFor(UUID userId) {
        if (userOne.getId().equals(userId)) userOneUnread = getUserOneUnread() + 1;
        if (userTwo.getId().equals(userId)) userTwoUnread = getUserTwoUnread() + 1;
    }

    public int unreadFor(UUID userId) {
        return userOne.getId().equals(userId) ? getUserOneUnread() : getUserTwoUnread();
    }

    public void clearUnreadFor(UUID userId) {
        if (userOne.getId().equals(userId)) userOneUnread = 0;
        if (userTwo.getId().equals(userId)) userTwoUnread = 0;
    }
}
