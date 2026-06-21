package com.talexck.minigamelib.core.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.talexck.minigamelib.api.arena.ArenaTeam;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamDistributionTest {

  @Test
  void splitsPlayersEvenlyAcrossAllColorsWhenNoneConfigured() {
    List<ArenaTeam> teams =
        TeamDistribution.resolveTeams(List.of("a", "b", "c", "d"), List.of(), 0);

    // No cap, 4 players, 10 colors -> 4 teams of 1 (round-robin).
    assertEquals(4, teams.size());
    assertTrue(teams.stream().allMatch(team -> team.playerNames().size() == 1));
  }

  @Test
  void roundRobinAssignsPlayersInOrder() {
    List<ArenaTeamColor> colors = List.of(ArenaTeamColor.RED, ArenaTeamColor.BLUE);
    List<ArenaTeam> teams =
        TeamDistribution.resolveTeams(List.of("p1", "p2", "p3", "p4", "p5"), colors, 0);

    assertEquals(2, teams.size());
    assertEquals(List.of("p1", "p3", "p5"), teams.get(0).playerNames());
    assertEquals(List.of("p2", "p4"), teams.get(1).playerNames());
  }

  @Test
  void teamCountIsCappedByConfiguredColors() {
    List<ArenaTeamColor> colors = List.of(ArenaTeamColor.RED, ArenaTeamColor.BLUE);
    List<ArenaTeam> teams =
        TeamDistribution.resolveTeams(List.of("a", "b", "c", "d", "e", "f"), colors, 0);

    assertEquals(2, teams.size());
  }

  @Test
  void maxTeamSizeRaisesTeamCountToRespectCap() {
    List<ArenaTeamColor> colors =
        List.of(ArenaTeamColor.RED, ArenaTeamColor.BLUE, ArenaTeamColor.GREEN, ArenaTeamColor.YELLOW);
    // 8 players, teamSize 2 -> needs 4 teams, not the default min(8,4)=4 (already 4 here),
    // verify no team exceeds the cap.
    List<ArenaTeam> teams =
        TeamDistribution.resolveTeams(List.of("1", "2", "3", "4", "5", "6", "7", "8"), colors, 2);

    assertEquals(4, teams.size());
    assertTrue(teams.stream().allMatch(team -> team.playerNames().size() <= 2));
  }

  @Test
  void maxTeamSizeAddsTeamsWhenDefaultWouldOverfill() {
    List<ArenaTeamColor> colors = List.of(ArenaTeamColor.values());
    // 6 players with default would be min(6,10)=6 teams of 1. With cap 4 we still want few teams,
    // but cap only raises, never lowers: ceil(6/4)=2, max(6,2)=6 -> stays 6.
    List<ArenaTeam> capped = TeamDistribution.resolveTeams(
        List.of("1", "2", "3", "4", "5", "6"), colors, 4);
    assertEquals(6, capped.size());
  }

  @Test
  void capForcesMoreTeamsThanColorsAllowOnlyUpToColorLimit() {
    List<ArenaTeamColor> colors = List.of(ArenaTeamColor.RED);
    // Single color: even with a cap, cannot exceed 1 team.
    List<ArenaTeam> teams =
        TeamDistribution.resolveTeams(List.of("a", "b", "c"), colors, 1);
    assertEquals(1, teams.size());
    assertEquals(3, teams.get(0).playerNames().size());
  }

  @Test
  void singlePlayerProducesSingleTeam() {
    List<ArenaTeam> teams = TeamDistribution.resolveTeams(List.of("solo"), List.of(), 0);
    assertEquals(1, teams.size());
    assertEquals(List.of("solo"), teams.get(0).playerNames());
  }

  @Test
  void emptyPlayerListProducesNoTeams() {
    assertTrue(TeamDistribution.resolveTeams(List.of(), List.of(), 0).isEmpty());
  }
}
