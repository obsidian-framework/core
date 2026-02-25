package fr.kainovaii.obsidian.http.controller;

import spark.Request;
import spark.Response;

public interface AdviceControllerInterface
{
    public void applyGlobals(Request req, Response res);
}
