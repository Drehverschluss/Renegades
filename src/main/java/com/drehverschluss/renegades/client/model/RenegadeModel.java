package com.drehverschluss.renegades.client.model;

import com.drehverschluss.renegades.Renegades;
import com.drehverschluss.renegades.entity.RenegadeEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Shared humanoid model for all renegades: geo/entity/renegade.geo.json and animations/entity/renegade.animation.json.
 */
public class RenegadeModel extends DefaultedEntityGeoModel<RenegadeEntity> {
    // Placeholder skin until renegades get their own textures; any 64x64 player skin fits the UV layout.
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public RenegadeModel() {
        super(ResourceLocation.fromNamespaceAndPath(Renegades.MODID, "renegade"), true);
    }

    @Override
    public ResourceLocation getTextureResource(RenegadeEntity animatable) {
        return TEXTURE;
    }
}