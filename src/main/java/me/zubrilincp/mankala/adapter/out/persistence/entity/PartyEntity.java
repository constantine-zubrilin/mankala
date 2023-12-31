package me.zubrilincp.mankala.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.zubrilincp.mankala.domain.commons.PartyState;
import me.zubrilincp.mankala.domain.commons.Player;
import me.zubrilincp.mankala.domain.model.Party;

@Entity
@Table(name = "party")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class PartyEntity {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private PartyState state;

  @Embedded private BoardEntity board;

  @Enumerated(EnumType.STRING)
  @Column(name = "player_turn", nullable = false)
  Player playerTurn;

  @Version
  @Column(name = "version", nullable = false)
  private int version;

  public PartyEntity(Party party) {
    this(
        party.id(),
        party.state(),
        new BoardEntity(party.board()),
        party.playerTurn(),
        party.version());
  }

  public Party toParty() {
    return new Party(id, state, board.toBoard(), playerTurn, version);
  }
}
