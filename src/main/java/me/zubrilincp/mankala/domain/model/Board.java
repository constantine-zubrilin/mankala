package me.zubrilincp.mankala.domain.model;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import me.zubrilincp.mankala.domain.commons.PitType;
import me.zubrilincp.mankala.domain.commons.Player;
import me.zubrilincp.mankala.domain.exception.InvalidPitOwnerException;
import me.zubrilincp.mankala.domain.exception.InvalidPitStonesCountException;
import me.zubrilincp.mankala.domain.exception.InvalidPitTypeException;
import me.zubrilincp.mankala.domain.exception.PitIndexOutOfBoundException;
import me.zubrilincp.mankala.domain.exception.validation.board.IllegalBoardArgumentException;
import me.zubrilincp.mankala.domain.exception.validation.board.InvalidPitsSetupException;

public record Board(List<Pit> pits, Integer lastUsedPitIndex) {

  public Board(List<Pit> pits, Integer lastUsedPitIndex) {
    if (pits == null) {
      throw new IllegalBoardArgumentException("Pits cannot be null");
    }

    pits.forEach(
        pit -> {
          if (pit == null) {
            throw new IllegalBoardArgumentException("Pits cannot contain null");
          }
        });

    if (pits.stream().filter(pit -> pit.player() == Player.PLAYER_ONE).count()
        != pits.stream().filter(pit -> pit.player() == Player.PLAYER_TWO).count()) {
      throw new InvalidPitsSetupException("Players must have the same number of pits");
    }

    validatePits(pits.subList(0, pits.size() / 2), Player.PLAYER_ONE);
    validatePits(pits.subList(pits.size() / 2, pits.size()), Player.PLAYER_TWO);

    this.pits = pits.stream().map(pit -> new Pit(pit.player(), pit.type(), pit.stones())).toList();
    this.lastUsedPitIndex = lastUsedPitIndex;
  }

  private void validatePits(List<Pit> pits, Player player) {
    pits.forEach(
        pit -> {
          if (pit.player() != player) {
            throw new InvalidPitsSetupException("Pits must be for the same player");
          }
        });
    if (pits.stream().filter(pit -> pit.type() == PitType.STORE).count() != 1) {
      throw new InvalidPitsSetupException("Pits must have 1 store pit for each player");
    }
    if (pits.stream().noneMatch(pit -> pit.type() == PitType.HOUSE)) {
      throw new InvalidPitsSetupException("Pits must have at least 1 house pit for each player");
    }
    if (pits.get(pits.size() - 1).type() != PitType.STORE) {
      throw new InvalidPitsSetupException("Pits must have STORE pit as last pit for each player");
    }
  }

  public List<Pit> pits() {
    return pits.stream()
        .map(pit -> new Pit(pit.player(), pit.type(), pit.stones()))
        .collect(Collectors.toList());
  }

  public Board makeMove(Player player, int pitIndex) {
    if (pits.size() <= pitIndex || pitIndex < 0) {
      throw new PitIndexOutOfBoundException("Pit index is out of bounds");
    }
    if (pits().get(pitIndex).player() != player) {
      throw new InvalidPitOwnerException("Player cannot start move from this pit, it's not theirs");
    }
    if (pits().get(pitIndex).type() != PitType.HOUSE) {
      throw new InvalidPitTypeException(
          "Player cannot start move from this pit, it's not HOME pit");
    }
    if (pits().get(pitIndex).stones() <= 0) {
      throw new InvalidPitStonesCountException(
          "Player cannot start move from this pit, there is no stones");
    }

    List<Pit> pits = pits();

    Pit startPit = pits.get(pitIndex);

    long stonesInHand = startPit.stones();
    pits.set(pitIndex, new Pit(startPit.player(), startPit.type(), 0));

    do {
      pitIndex = (pitIndex + 1) % pits.size();

      Pit pit = pits.get(pitIndex);
      if (pit.type() == PitType.STORE && pit.player() != startPit.player()) {
        // Skip other player's STORE
        continue;
      }

      pits.set(pitIndex, new Pit(pit.player(), pit.type(), pit.stones() + 1));
      stonesInHand--;
    } while (stonesInHand > 0);

    Pit lastUsedPit = pits.get(pitIndex);
    if (lastUsedPit.player() == startPit.player()
        && lastUsedPit.type() == PitType.HOUSE
        && lastUsedPit.stones() == 1) {
      pits = takeOppositeStones(pits, pitIndex);
    }

    if (noStonesInPlayerHouses(pits, Player.PLAYER_ONE)
        || noStonesInPlayerHouses(pits, Player.PLAYER_TWO)) {
      pits = putPlayerStonesInStore(pits);
    }

    return new Board(pits, pitIndex);
  }

  public Pit lastUsedPit() {
    return pits.get(lastUsedPitIndex);
  }

  private List<Pit> takeOppositeStones(List<Pit> pits, int lastPitIndex) {
    Pit lastPit = pits.get(lastPitIndex);

    int oppositePitIndex = pits.size() - lastPitIndex - 2;
    Pit oppositePit = pits.get(oppositePitIndex);

    if (oppositePit.player() != lastPit.player()
        && oppositePit.type() == PitType.HOUSE
        && oppositePit.stones() > 0) {
      pits.set(lastPitIndex, new Pit(lastPit.player(), lastPit.type(), 0));
      pits.set(oppositePitIndex, new Pit(oppositePit.player(), oppositePit.type(), 0));

      return pits.stream()
          .map(
              pit -> {
                if (pit.player() == lastPit.player() && pit.type() == PitType.STORE) {
                  return new Pit(
                      pit.player(),
                      pit.type(),
                      pit.stones() + lastPit.stones() + oppositePit.stones());
                } else {
                  return pit;
                }
              })
          .collect(Collectors.toList());
    }

    return pits;
  }

  private boolean noStonesInPlayerHouses(List<Pit> pits, Player player) {
    return pits.stream()
        .noneMatch(
            pit -> pit.player() == player && pit.type() == PitType.HOUSE && pit.stones() > 0);
  }

  private List<Pit> putPlayerStonesInStore(List<Pit> pits) {
    Map<Player, AtomicLong> stonesCount = new EnumMap<>(Player.class);
    Arrays.stream(Player.values()).forEach(player -> stonesCount.put(player, new AtomicLong(0)));
    pits.forEach(pit -> stonesCount.get(pit.player()).addAndGet(pit.stones()));

    return pits.stream()
        .map(
            pit -> {
              if (pit.type() == PitType.STORE) {
                return new Pit(pit.player(), pit.type(), stonesCount.get(pit.player()).longValue());
              } else {
                return new Pit(pit.player(), pit.type(), 0);
              }
            })
        .collect(Collectors.toList());
  }
}
