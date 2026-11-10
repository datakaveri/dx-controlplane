package org.cdpg.dx.deploy;

import org.cdpg.dx.deploy.util.ConfigHelper;

public class Deployer {
  public static void main(String[] args) {
    BaseDeployer.launch(args, "TGDEX Control Panel Server", ConfigHelper::mergeRequiredConfigs);
  }
}
