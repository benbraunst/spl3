#pragma once

#include <string>
#include <vector>
#include <map>
#include <fstream>
#include "event.h"

class GameManager {
private:
    // Game Name -> (User Name -> List of Events)
    std::map<std::string, std::map<std::string, std::vector<Event>>> events;

public:
    GameManager() : events() {}

    void addEvent(const Event& event, std::string user, std::string gameName) {
        events[gameName][user].push_back(event);
    }

    std::string generateSummary(std::string gameName, std::string user) {
        if (events.find(gameName) == events.end() || events[gameName].find(user) == events[gameName].end()) {
            return "No events found for game " + gameName + " and user " + user;
        }

        const std::vector<Event>& userEvents = events[gameName][user];
        if (userEvents.empty()) {
             return "No events found for game " + gameName + " and user " + user;
        }

        // Basic info (taken from the first event, assuming consistency)
        std::string teamA = userEvents[0].get_team_a_name();
        std::string teamB = userEvents[0].get_team_b_name();

        std::string summary = teamA + " vs " + teamB + "\n";
        summary += "Game stats:\n";
        summary += "General stats:\n";

        // Aggregate stats
        std::map<std::string, std::string> finalGameUpdates;
        std::map<std::string, std::string> finalTeamAUpdates;
        std::map<std::string, std::string> finalTeamBUpdates;

        for (const auto& event : userEvents) {
            for (const auto& pair : event.get_game_updates()) finalGameUpdates[pair.first] = pair.second;
            for (const auto& pair : event.get_team_a_updates()) finalTeamAUpdates[pair.first] = pair.second;
            for (const auto& pair : event.get_team_b_updates()) finalTeamBUpdates[pair.first] = pair.second;
        }

        for (const auto& pair : finalGameUpdates) {
            summary += pair.first + ": " + pair.second + "\n";
        }

        summary += teamA + " stats:\n";
        for (const auto& pair : finalTeamAUpdates) {
            summary += pair.first + ": " + pair.second + "\n";
        }

        summary += teamB + " stats:\n";
        for (const auto& pair : finalTeamBUpdates) {
            summary += pair.first + ": " + pair.second + "\n";
        }

        summary += "Game event reports:\n";
        for (const auto& event : userEvents) {
            summary += std::to_string(event.get_time()) + " - " + event.get_name() + ":\n\n";
            summary += event.get_discription() + "\n\n";
        }

        return summary;
    }
    
    void writeSummaryToFile(std::string gameName, std::string user, std::string filePath) {
        std::string summary = generateSummary(gameName, user);
        std::ofstream outFile(filePath);
        if (outFile.is_open()) {
            outFile << summary;
            outFile.close();
        }
    }
};
