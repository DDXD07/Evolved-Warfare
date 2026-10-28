package com.evolvedwarfare.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Si está Mod Menu, el botón de configuración del mod abre nuestra pantalla. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
