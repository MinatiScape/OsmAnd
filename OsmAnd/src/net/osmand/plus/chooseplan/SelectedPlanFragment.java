package net.osmand.plus.chooseplan;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import net.osmand.AndroidUtils;
import net.osmand.PlatformUtil;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.Version;
import net.osmand.plus.chooseplan.button.PriceButton;
import net.osmand.plus.inapp.InAppPurchases.InAppSubscription;
import net.osmand.plus.wikipedia.WikipediaDialogFragment;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.osmand.plus.liveupdates.LiveUpdatesSettingsBottomSheet.getActiveColorId;

public abstract class SelectedPlanFragment extends BasePurchaseFragment {

	public static final String TAG = SelectedPlanFragment.class.getSimpleName();
	private static final Log LOG = PlatformUtil.getLog(SelectedPlanFragment.class);

	private static final String PURCHASES_INFO = "https://docs.osmand.net/en/main@latest/osmand/purchases/android";

	protected List<OsmAndFeature> includedFeatures = new ArrayList<>();
	protected List<OsmAndFeature> noIncludedFeatures = new ArrayList<>();
	protected List<PriceButton<?>> priceButtons = new ArrayList<>();
	private Map<PriceButton<?>, View> buttonViews = new HashMap<>();
	private PriceButton<?> selectedPriceButton;

	@Override
	protected int getLayoutId() {
		return R.layout.fragment_selected_plan;
	}

	@Override
	protected void initData(@Nullable Bundle args) {
		collectPriceButtons(priceButtons);
		if (priceButtons.size() > 0) {
			selectedPriceButton = priceButtons.get(0);
		}
	}

	@Override
	protected void initView() {
		setupToolbar();
		setupHeader();
		createFeaturesPreview();
		setupLearnMoreButton();
		setupPriceButtons();
		setupApplyButton();
		setupDescription();
		setupRestoreButton();
		createIncludesList();
		fullUpdate();
	}

	private void fullUpdate() {
		updateToolbar();
	}

	private void setupToolbar() {
		ImageView backBtn = view.findViewById(R.id.button_back);
		backBtn.setImageResource(AndroidUtils.getNavigationIconResId(app));
		backBtn.setOnClickListener(v -> dismiss());

		ImageView helpBtn = view.findViewById(R.id.button_help);
		helpBtn.setOnClickListener(v ->
				WikipediaDialogFragment.showFullArticle(requireActivity(), Uri.parse(PURCHASES_INFO), nightMode));
	}

	@Override
	protected void updateToolbar(int verticalOffset) {
		float absOffset = Math.abs(verticalOffset);
		float totalScrollRange = appBar.getTotalScrollRange();
		boolean collapsed = Math.abs(verticalOffset) == appBar.getTotalScrollRange();

		float alpha = UiUtilities.getProportionalAlpha(0, totalScrollRange * 0.75f, absOffset);
		float inverseAlpha = 1.0f - UiUtilities.getProportionalAlpha(0, totalScrollRange, absOffset);

		int defaultLinksColor = ContextCompat.getColor(app, getToolbarLinksColor());
		int activeLinksColor = ContextCompat.getColor(app, getActiveToolbarLinksColor());
		int headerBgColor = ContextCompat.getColor(app, getHeaderBgColorId());
		int activeColor = ContextCompat.getColor(app, getActiveColorId(nightMode));

		View header = view.findViewById(R.id.header);
		header.setAlpha(alpha);

		ImageView icBack = view.findViewById(R.id.button_back);
		ImageView icInfo = view.findViewById(R.id.button_help);
		int iconsColor = UiUtilities.getProportionalColorMix(defaultLinksColor, activeLinksColor, 0, totalScrollRange, Math.abs(verticalOffset));
		icBack.setColorFilter(iconsColor);
		icInfo.setColorFilter(iconsColor);

		TextView tvTitle = view.findViewById(R.id.toolbar_title);
		tvTitle.setTextColor(activeLinksColor);
		tvTitle.setText(getHeader());
		tvTitle.setAlpha(inverseAlpha);

		int toolbarColor = UiUtilities.getProportionalColorMix(headerBgColor, activeColor, 0, totalScrollRange, Math.abs(verticalOffset));
		appBar.setBackgroundColor(toolbarColor);
		Dialog dialog = getDialog();
		if (Build.VERSION.SDK_INT >= 21 && dialog != null && dialog.getWindow() != null) {
			Window window = dialog.getWindow();
			window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			window.setStatusBarColor(toolbarColor);
			if (Build.VERSION.SDK_INT >= 23 && !nightMode) {
				window.getDecorView().setSystemUiVisibility(collapsed ?
						view.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR :
						view.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
			}
		}

		View shadow = view.findViewById(R.id.shadowView);
		shadow.setAlpha(inverseAlpha);
	}

	private void setupHeader() {
		View mainPart = view.findViewById(R.id.main_part);
		int bgColor = ContextCompat.getColor(app, getHeaderBgColorId());
		mainPart.setBackgroundColor(bgColor);

		TextView tvTitle = view.findViewById(R.id.header_title);
		tvTitle.setText(getHeader());

		TextView tvDescription = view.findViewById(R.id.header_descr);
		tvDescription.setText(getTagline());

		ImageView ivIcon = view.findViewById(R.id.header_icon);
		ivIcon.setImageResource(getHeaderIconId());
	}

	private void createFeaturesPreview() {
		LinearLayout container = view.findViewById(R.id.features_list);
		for (OsmAndFeature feature : features) {
			View itemView = inflater.inflate(R.layout.purchase_dialog_preview_list_item, container, false);
			bindFeatureItem(itemView, feature);
			container.addView(itemView);
		}
	}

	private void setupLearnMoreButton() {
		View btn = view.findViewById(R.id.learn_more_button);
		btn.setOnClickListener(v -> {
			View includesContainer = view.findViewById(R.id.list_included);
			scrollView.smoothScrollTo(0, includesContainer.getTop());
			appBar.setExpanded(false);
		});
	}

	protected void bindFeatureItem(@NonNull View itemView,
	                               @NonNull OsmAndFeature feature) {
		bindFeatureItem(itemView, feature, false);
		ImageView ivCheckmark = itemView.findViewById(R.id.checkmark);
		if (includedFeatures.contains(feature)) {
			ivCheckmark.setVisibility(View.VISIBLE);
			ivCheckmark.setImageDrawable(getPreviewListCheckmark());
		} else {
			ivCheckmark.setVisibility(View.GONE);
		}
	}

	private void setupPriceButtons() {
		LinearLayout container = view.findViewById(R.id.price_block);
		container.removeAllViews();

		for (PriceButton<?> btn : priceButtons) {
			View itemView = inflater.inflate(R.layout.purchase_dialog_btn_payment, container, false);
			TextView tvTitle = itemView.findViewById(R.id.title);
			TextView tvPrice = itemView.findViewById(R.id.price);
			TextView tvDiscount = itemView.findViewById(R.id.discount);
			TextView tvDesc = itemView.findViewById(R.id.description);

			tvTitle.setText(btn.getTitle());
			tvPrice.setText(btn.getPrice());
			if (!Algorithms.isEmpty(btn.getDiscount())) {
				tvDiscount.setText(btn.getDiscount());
				tvDiscount.setVisibility(View.VISIBLE);
				if (!Algorithms.isEmpty(btn.getRegularPrice())) {
					String pattern = getString(R.string.ltr_or_rtl_combine_via_colon);
					String regularPrice = String.format(pattern, getString(R.string.regular_price), btn.getRegularPrice());
					tvDesc.setText(regularPrice);
					tvDesc.setVisibility(View.VISIBLE);
				}
			}

			itemView.setOnClickListener(v -> {
				selectedPriceButton = btn;
				updateButtons();
			});

			buttonViews.put(btn, itemView);
			container.addView(itemView);
		}

		updateButtons();
	}

	private void updateButtons() {
		for (PriceButton<?> key : buttonViews.keySet()) {
			View itemView = buttonViews.get(key);
			if (itemView == null) continue;

			ImageView ivCheckmark = itemView.findViewById(R.id.icon);

			int colorNoAlpha = ContextCompat.getColor(app, getActiveColorId(nightMode));
			Drawable normal;
			if (key.equals(selectedPriceButton)) {
				ivCheckmark.setImageDrawable(getCheckmark());

				Drawable stroke = getActiveStrokeDrawable();
				int colorWithAlpha = getAlphaColor(colorNoAlpha, 0.1f);

				Drawable bgDrawable = app.getUIUtilities().getPaintedIcon(R.drawable.rectangle_rounded, colorWithAlpha);
				Drawable[] layers = {bgDrawable, stroke};
				normal = new LayerDrawable(layers);
			} else {
				ivCheckmark.setImageDrawable(getEmptyCheckmark());
				normal = new ColorDrawable(Color.TRANSPARENT);
			}
			setupRoundedBackground(itemView, normal, colorNoAlpha);
		}

		if (selectedPriceButton != null) {
			View applyButton = view.findViewById(R.id.apply_button);
			TextView tvPrice = applyButton.findViewById(R.id.description);
			tvPrice.setText(selectedPriceButton.getPrice());
		}
	}

	private void setupApplyButton() {
		View itemView = view.findViewById(R.id.apply_button);

		TextView tvTitle = itemView.findViewById(R.id.title);
		tvTitle.setText(getString(R.string.complete_purchase));

		itemView.setOnClickListener(v ->  {
			if (selectedPriceButton != null && getActivity() != null) {
				selectedPriceButton.onApply(getActivity(), purchaseHelper);
			}
		});

		int activeColor = ContextCompat.getColor(app, getActiveColorId(nightMode));
		Drawable normal = createRoundedDrawable(activeColor);;
		setupRoundedBackground(itemView, normal, activeColor);
	}

	private void setupDescription() {
		String cancelDesc = getString(Version.isHuawei() ?
				R.string.cancel_anytime_in_huawei_appgallery :
				R.string.cancel_anytime_in_gplay);

		String commonDesc = getString(Version.isHuawei() ?
				R.string.osm_live_payment_subscription_management_hw :
				R.string.osm_live_payment_subscription_management);

		((TextView) view.findViewById(R.id.cancel_description)).setText(cancelDesc);
		((TextView) view.findViewById(R.id.plan_info_description)).setText(commonDesc);
	}

	private void setupRestoreButton() {
		View button = view.findViewById(R.id.button_restore);
		button.setOnClickListener(v -> purchaseHelper.requestInventory());
		setupRoundedBackground(button);
	}

	private void createIncludesList() {
		LinearLayout container = view.findViewById(R.id.list_included);
		Map<String, List<OsmAndFeature>> chapters = new LinkedHashMap<>();
		chapters.put(getString(R.string.ltr_or_rtl_combine_via_colon, getString(R.string.shared_string_includes), ""), includedFeatures);
		chapters.put(getString(R.string.ltr_or_rtl_combine_via_colon, getString(R.string.shared_string_not_included), ""), noIncludedFeatures);

		for (String key : chapters.keySet()) {
			List<OsmAndFeature> features = chapters.get(key);
			if (features != null && features.size() > 0) {
				View v = inflater.inflate(R.layout.purchase_dialog_includes_block_header, container, false);
				TextView tvTitle = v.findViewById(R.id.title);
				tvTitle.setText(key);
				container.addView(v);
				for (OsmAndFeature feature : features) {
					View itemView = inflater.inflate(R.layout.purchase_dialog_includes_block_item, container, false);
					if (features.indexOf(feature) == 0) {
						itemView.findViewById(R.id.top_padding).setVisibility(View.GONE);
					}
					bindIncludesFeatureItem(itemView, feature);
					container.addView(itemView);
				}
			}
		}
	}

	private void bindIncludesFeatureItem(View itemView, OsmAndFeature feature) {
		bindFeatureItem(itemView, feature, true);

		TextView tvDescription = itemView.findViewById(R.id.description);
		tvDescription.setText(getString(feature.getDescriptionId()));

		int iconBgColor = ContextCompat.getColor(app, feature.isAvailableInMapsPlus() ?
				R.color.maps_plus_item_bg :
				R.color.osmand_pro_item_bg);
		setupIconBackground(itemView.findViewById(R.id.icon_background), getAlphaColor(iconBgColor, 0.2f));
	}

	protected Drawable getCheckmark() {
		return getIcon(nightMode ?
				R.drawable.ic_action_checkmark_colored_night :
				R.drawable.ic_action_checkmark_colored_day);
	}

	protected Drawable getEmptyCheckmark() {
		return getIcon(nightMode ?
				R.drawable.ic_action_radio_button_night :
				R.drawable.ic_action_radio_button_day);
	}

	@ColorRes
	protected int getToolbarLinksColor() {
		return nightMode ?
				R.color.purchase_sc_toolbar_active_dark :
				R.color.purchase_sc_toolbar_active_light;
	}

	@ColorRes
	protected int getActiveToolbarLinksColor() {
		return nightMode ?
				R.color.active_buttons_and_links_text_dark :
				R.color.active_buttons_and_links_text_light;
	}

	protected abstract void collectPriceButtons(List<PriceButton<?>> priceButtons);

	protected abstract List<InAppSubscription> getVisibleSubscriptions();

	protected abstract Drawable getPreviewListCheckmark();

	protected abstract int getHeaderBgColorId();

	protected abstract String getHeader();

	protected abstract String getTagline();

	protected abstract int getHeaderIconId();

}
