package com.obsidian.core.livecomponents.pebble;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.tokenParser.TokenParser;

import java.util.List;

/**
 * Pebble extension that registers the {@code {% component %}} tag.
 */
public class ComponentTagExtension extends AbstractExtension
{
    @Override
    public List<TokenParser> getTokenParsers() {
        System.out.println("ComponentTagExtension.getTokenParsers() called");
        return List.of(new ComponentTag());
    }
}