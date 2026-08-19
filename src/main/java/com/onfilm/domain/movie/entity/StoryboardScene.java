package com.onfilm.domain.movie.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryboardScene {

    public static final int TITLE_MAX_LENGTH = 120;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = TITLE_MAX_LENGTH)
    private String title;

    @Lob
    @Column
    private String scriptHtml;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private StoryboardProject project;

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "sort_order")
    private List<StoryboardCard> cards = new ArrayList<>();

    private StoryboardScene(String title, String scriptHtml) {
        this.title = normalizeTitle(title);
        this.scriptHtml = scriptHtml;
    }

    static StoryboardScene create(String title, String scriptHtml) {
        return new StoryboardScene(title, scriptHtml);
    }

    void attachProject(StoryboardProject project) {
        if (project == null) {
            throw new IllegalArgumentException("project is required");
        }
        if (this.project != null && this.project != project) {
            throw new IllegalStateException(
                    "storyboardScene already belongs to another project"
            );
        }
        this.project = project;
    }

    void detachProject(StoryboardProject project) {
        if (this.project == project) {
            this.project = null;
        }
    }

    public void changeContent(String title, String scriptHtml) {
        String normalizedTitle = normalizeTitle(title);
        this.title = normalizedTitle;
        this.scriptHtml = scriptHtml;
    }

    public StoryboardCard addCard(String imageKey) {
        StoryboardCard card = StoryboardCard.create(imageKey);
        addCard(card);
        return card;
    }

    void addCard(StoryboardCard card) {
        StoryboardCard requiredCard = require(card, "card");
        if (cards.contains(requiredCard)) {
            throw new IllegalArgumentException("duplicate storyboard card");
        }

        requiredCard.attachScene(this);
        cards.add(requiredCard);
    }

    public void removeCard(StoryboardCard card) {
        StoryboardCard requiredCard = require(card, "card");
        if (!cards.remove(requiredCard)) {
            throw new IllegalArgumentException("card does not belong to scene");
        }

        requiredCard.detachScene(this);
    }

    public CardReplacementResult replaceCards(List<CardChange> changes) {
        List<CardChange> requiredChanges = require(changes, "cardChanges");
        Map<Long, StoryboardCard> existingById = existingCardsById();
        Set<Long> requestedIds = new HashSet<>();
        List<ResolvedCardChange> resolvedChanges = new ArrayList<>();

        for (CardChange change : requiredChanges) {
            CardChange requiredChange = require(change, "cardChange");
            String normalizedImageKey = StoryboardCard.normalizeImageKey(
                    requiredChange.imageKey()
            );
            Long cardId = requiredChange.cardId();
            if (cardId == null) {
                resolvedChanges.add(new ResolvedCardChange(null, normalizedImageKey));
                continue;
            }
            if (!requestedIds.add(cardId)) {
                throw new IllegalArgumentException("duplicate cardId");
            }

            StoryboardCard existingCard = existingById.get(cardId);
            if (existingCard == null) {
                throw new IllegalArgumentException("card does not belong to scene");
            }
            resolvedChanges.add(new ResolvedCardChange(existingCard, normalizedImageKey));
        }

        Set<StoryboardCard> retainedCards = new HashSet<>();
        Set<String> obsoleteImageKeys = new LinkedHashSet<>();
        List<StoryboardCard> replacementCards = new ArrayList<>();
        for (ResolvedCardChange change : resolvedChanges) {
            StoryboardCard card = change.card();
            if (card == null) {
                card = StoryboardCard.create(change.imageKey());
                card.attachScene(this);
            } else {
                retainedCards.add(card);
                if (!Objects.equals(card.getImageKey(), change.imageKey())) {
                    addImageKey(obsoleteImageKeys, card.getImageKey());
                    card.changeImageKey(change.imageKey());
                }
            }
            replacementCards.add(card);
        }

        for (StoryboardCard card : cards) {
            if (!retainedCards.contains(card)) {
                addImageKey(obsoleteImageKeys, card.getImageKey());
                card.detachScene(this);
            }
        }

        Set<String> retainedImageKeys = new HashSet<>();
        for (StoryboardCard card : replacementCards) {
            addImageKey(retainedImageKeys, card.getImageKey());
        }
        obsoleteImageKeys.removeAll(retainedImageKeys);

        cards.clear();
        cards.addAll(replacementCards);
        return new CardReplacementResult(new ArrayList<>(obsoleteImageKeys));
    }

    public List<StoryboardCard> getCards() {
        return Collections.unmodifiableList(cards);
    }

    private Map<Long, StoryboardCard> existingCardsById() {
        Map<Long, StoryboardCard> existingById = new LinkedHashMap<>();
        for (StoryboardCard card : cards) {
            if (card.getId() != null) {
                existingById.put(card.getId(), card);
            }
        }
        return existingById;
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }

        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "scene title is too long (max " + TITLE_MAX_LENGTH + ")"
            );
        }
        return trimmed;
    }

    private static void addImageKey(Set<String> keys, String key) {
        if (key != null) {
            keys.add(key);
        }
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    public record CardChange(Long cardId, String imageKey) {
    }

    public record CardReplacementResult(List<String> obsoleteImageKeys) {
        public CardReplacementResult {
            obsoleteImageKeys = List.copyOf(obsoleteImageKeys);
        }
    }

    private record ResolvedCardChange(StoryboardCard card, String imageKey) {
    }
}
