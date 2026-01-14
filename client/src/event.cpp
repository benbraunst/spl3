#include "../include/event.h"
#include "../include/json.hpp"
#include <iostream>
#include <fstream>
#include <string>
#include <map>
#include <vector>
#include <sstream>
using json = nlohmann::json;

Event::Event(std::string team_a_name, std::string team_b_name, std::string name, int time,
             std::map<std::string, std::string> game_updates, std::map<std::string, std::string> team_a_updates,
             std::map<std::string, std::string> team_b_updates, std::string discription)
    : team_a_name(team_a_name), team_b_name(team_b_name), name(name),
      time(time), game_updates(game_updates), team_a_updates(team_a_updates),
      team_b_updates(team_b_updates), description(discription)
{
}

Event::~Event()
{
}

const std::string &Event::get_team_a_name() const
{
    return this->team_a_name;
}

const std::string &Event::get_team_b_name() const
{
    return this->team_b_name;
}

const std::string &Event::get_name() const
{
    return this->name;
}

int Event::get_time() const
{
    return this->time;
}

const std::map<std::string, std::string> &Event::get_game_updates() const
{
    return this->game_updates;
}

const std::map<std::string, std::string> &Event::get_team_a_updates() const
{
    return this->team_a_updates;
}

const std::map<std::string, std::string> &Event::get_team_b_updates() const
{
    return this->team_b_updates;
}

const std::string &Event::get_discription() const
{
    return this->description;
}

Event::Event(const std::string &frame_body) : team_a_name(""), team_b_name(""), name(""), time(0), game_updates(), team_a_updates(), team_b_updates(), description("")
{
    std::stringstream ss(frame_body);
    std::string line;
    std::string current_map = "";

    while (std::getline(ss, line)) {
        if (line.back() == '\r') line.pop_back(); // Handle potential Windows line endings
        if (line.empty()) continue;

        if (line.find("team a:") == 0) {
            team_a_name = line.substr(7);
        } else if (line.find("team b:") == 0) {
            team_b_name = line.substr(7);
        } else if (line.find("event name:") == 0) {
            name = line.substr(11);
        } else if (line.find("time:") == 0) {
            time = std::stoi(line.substr(5));
        } else if (line == "general game updates:") {
            current_map = "general";
        } else if (line == "team a updates:") {
            current_map = "team_a";
        } else if (line == "team b updates:") {
            current_map = "team_b";
        } else if (line == "description:") {
            current_map = "description";
        } else {
            // Processing map entries or description
            if (current_map == "description") {
                if (!description.empty()) description += "\n";
                description += line;
            } else if (line[0] == '\t') { // Tab indented header
                size_t colon = line.find(':');
                if (colon != std::string::npos) {
                    std::string key = line.substr(1, colon - 1);
                    std::string val = line.substr(colon + 1);
                    if (current_map == "general") game_updates[key] = val;
                    else if (current_map == "team_a") team_a_updates[key] = val;
                    else if (current_map == "team_b") team_b_updates[key] = val;
                }
            }
        }
    }
}

names_and_events parseEventsFile(std::string json_path)
{
    std::ifstream f(json_path);
    json data = json::parse(f);

    std::string team_a_name = data["team a"];
    std::string team_b_name = data["team b"];

    // run over all the events and convert them to Event objects
    std::vector<Event> events;
    for (auto &event : data["events"])
    {
        std::string name = event["event name"];
        int time = event["time"];
        std::string description = event["description"];
        std::map<std::string, std::string> game_updates;
        std::map<std::string, std::string> team_a_updates;
        std::map<std::string, std::string> team_b_updates;
        for (auto &update : event["general game updates"].items())
        {
            if (update.value().is_string())
                game_updates[update.key()] = update.value();
            else
                game_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team a updates"].items())
        {
            if (update.value().is_string())
                team_a_updates[update.key()] = update.value();
            else
                team_a_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team b updates"].items())
        {
            if (update.value().is_string())
                team_b_updates[update.key()] = update.value();
            else
                team_b_updates[update.key()] = update.value().dump();
        }
        
        events.push_back(Event(team_a_name, team_b_name, name, time, game_updates, team_a_updates, team_b_updates, description));
    }
    names_and_events events_and_names{team_a_name, team_b_name, events};

    return events_and_names;
}