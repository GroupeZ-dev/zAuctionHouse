package fr.maxlego08.zauctionhouse.api.storage.dto;
import java.util.UUID;
public record AdminPlayerDTO(UUID uniqueId, String name, long listed, long sold, long purchased) {}
