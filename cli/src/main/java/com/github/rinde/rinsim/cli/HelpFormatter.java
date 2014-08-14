package com.github.rinde.rinsim.cli;

/**
 * Implementations should create a formatted string containing all information
 * about a menu.
 * @author Rinde van Lon 
 */
public interface HelpFormatter {

  /**
   * Creates a formatted help string.
   * @param menu The menu to create the help for.
   * @return A formatted string containing all help information of the specified
   *         menu.
   */
  String format(Menu menu);

}
