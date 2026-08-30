package openrtm.games;

public record ClientLayout(long entityBase, int entitySize, int playerStateOffset, int nameOffset, int maxClients) {
}
