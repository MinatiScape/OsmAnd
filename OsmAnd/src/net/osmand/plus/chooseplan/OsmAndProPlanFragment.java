package net.osmand.plus.chooseplan;

import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import net.osmand.plus.R;
import net.osmand.plus.chooseplan.button.PriceButton;
import net.osmand.plus.chooseplan.button.PriceButtonsUtils;
import net.osmand.plus.inapp.InAppPurchases;
import net.osmand.plus.inapp.InAppPurchases.InAppSubscription;

import java.util.ArrayList;
import java.util.List;

public class OsmAndProPlanFragment extends SelectedPlanFragment {

	public static void showInstance(@NonNull FragmentActivity activity) {
		OsmAndProPlanFragment fragment = new OsmAndProPlanFragment();
		fragment.show(activity.getSupportFragmentManager(), TAG);
	}


	@Override
	protected void initData(@Nullable Bundle args) {
		super.initData(args);
		features.remove(OsmAndFeature.MONTHLY_MAP_UPDATES);
		features.remove(OsmAndFeature.WIKIPEDIA);
		features.remove(OsmAndFeature.WIKIVOYAGE);

		includedFeatures.addAll(features);
	}

	@Override
	protected void collectPriceButtons(List<PriceButton<?>> priceButtons) {
		priceButtons.clear();
		priceButtons.addAll(PriceButtonsUtils.collectSubscriptions(app, purchaseHelper, getVisibleSubscriptions()));
	}

	@Override
	protected int getHeaderBgColorId() {
		return nightMode ?
				R.color.activity_background_color_dark :
				R.color.activity_background_color_light;
	}

	@Override
	protected String getHeader() {
		return getString(R.string.osmand_pro);
	}

	@Override
	protected String getTagline() {
		return getString(R.string.osmand_pro_tagline);
	}

	@Override
	protected int getHeaderIconId() {
		return R.drawable.ic_action_osmand_pro_logo;
	}

	@Override
	protected List<InAppSubscription> getVisibleSubscriptions() {
		InAppPurchases purchases = app.getInAppPurchaseHelper().getInAppPurchases();
		List<InAppSubscription> subscriptions = new ArrayList<>();
		List<InAppSubscription> visibleSubscriptions = purchaseHelper.getSubscriptions().getVisibleSubscriptions();
		for (InAppSubscription subscription : visibleSubscriptions) {
			if (purchases.isOsmAndProSubscription(subscription)) {
				subscriptions.add(subscription);
			}
		}
		return subscriptions;
	}

	@Override
	protected Drawable getPreviewListCheckmark() {
		return getCheckmark();
	}

}
