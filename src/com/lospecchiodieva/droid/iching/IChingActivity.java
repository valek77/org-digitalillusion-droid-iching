package com.lospecchiodieva.droid.iching;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Html;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.*;

import com.lospecchiodieva.droid.iching.anim.AnimCoin;
import com.lospecchiodieva.droid.iching.changinglines.ChangingLinesEvaluator;
import com.lospecchiodieva.droid.iching.connection.RemoteResolver;
import com.lospecchiodieva.droid.iching.utils.Consts;
import com.lospecchiodieva.droid.iching.utils.DataPersister;
import com.lospecchiodieva.droid.iching.utils.SettingsManager;
import com.lospecchiodieva.droid.iching.utils.SettingsManager.SETTINGS_MAP;
import com.lospecchiodieva.droid.iching.utils.Utils;
import com.lospecchiodieva.droid.iching.utils.lists.HistoryEntry;
import com.lospecchiodieva.droid.iching.utils.lists.ListItem2Adapter;
import com.lospecchiodieva.droid.iching.utils.lists.SettingsEntry;
import com.lospecchiodieva.droid.iching.utils.sql.HexSectionDataSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A complete I Ching oracle for Android OS
 *
 * @author digitalillusion
 */
public class IChingActivity extends IChingActivityRenderer {

  /**
   * The currently generated hexagram row *
   */
  protected int hexRow;
  /**
   * The currently generated hexagram *
   */
  protected int[] hex;
  /**
   * The hexagram transformed from the currently generated one *
   */
  protected int[] tHex;
  /**
   * Proxed changing lines evaluator *
   */
  private ChangingLinesEvaluator changingLinesEvaluator;

  /**
   * Move to the consult view
   */
  public void gotoConsult() {
    setContentView(com.lospecchiodieva.droid.iching.R.layout.consult);
    TextView tvQuestionShow = (TextView) findViewById(com.lospecchiodieva.droid.iching.R.id.tvQuestionConsult);
    tvQuestionShow.setText(current.question);

    for (int i = 0; i < hexRow; i++) {
      renderRow(i, hex[i], true, null, null);
    }

    if (hexRow < 6) {
      prepareDivinationMethod();
    } else {
      ((ImageView) findViewById(com.lospecchiodieva.droid.iching.R.id.picCoin01)).setVisibility(View.GONE);
      ((ImageView) findViewById(com.lospecchiodieva.droid.iching.R.id.picCoin02)).setVisibility(View.GONE);
      ((ImageView) findViewById(com.lospecchiodieva.droid.iching.R.id.picCoin03)).setVisibility(View.GONE);

      TextView tvInstructions = (TextView) findViewById(com.lospecchiodieva.droid.iching.R.id.tvInstructions);
      String hexMap = Utils.hexMap(hex);
      tvInstructions.setText(Utils.getResourceByName(com.lospecchiodieva.droid.iching.R.string.class, "hex" + hexMap));
      tvInstructions.setText(hexMap + " " + tvInstructions.getText());

      final Button btnReadDesc = new Button(getApplicationContext());
      final LinearLayout layout = (LinearLayout) findViewById(com.lospecchiodieva.droid.iching.R.id.layCoins);
      btnReadDesc.setText(com.lospecchiodieva.droid.iching.R.string.consult_read_desc);
      btnReadDesc.setOnTouchListener(new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
          if (event.getAction() == MotionEvent.ACTION_DOWN) {
            btnReadDesc.setVisibility(View.GONE);
            current.mode = READ_DESC_MODE.ORACLE;
            gotoReadDesc();

            // Save history entry
            HistoryEntry historyEntry = new HistoryEntry();
            historyEntry.setHex(hex);
            historyEntry.setChanging(current.changing);
            historyEntry.setTHex(tHex);
            historyEntry.setQuestion(current.question);
            historyEntry.setDate(new Date());

            historyList.add(0, historyEntry);
            DataPersister.saveHistory(historyList, IChingActivity.this);

            // Save all pending settings changes now that a new entry
            // has been added to the history
            settings.save(IChingActivity.this);

            return true;
          }
          return false;
        }
      });
      layout.addView(btnReadDesc);
    }
  }

  /**
   * Move to the main view
   */
  public void gotoMain() {
    setContentView(com.lospecchiodieva.droid.iching.R.layout.main);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    RemoteResolver.prepareRetryPopup();

    final EditText etQuestion = (EditText) findViewById(com.lospecchiodieva.droid.iching.R.id.etQuestion);
    etQuestion.setOnEditorActionListener(new OnEditorActionListener() {
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        current.question = ((EditText) v).getText().toString();
        return false;
      }
    });
    etQuestion.setOnFocusChangeListener(new OnFocusChangeListener() {
      public void onFocusChange(View v, boolean hasFocus) {
        // Close keyboard on lose focus
        if (v.getId() == com.lospecchiodieva.droid.iching.R.id.etQuestion && !hasFocus) {
          InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
          imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
      }
    });
    if (current.question != null) {
      etQuestion.setText(current.question);
    }

    final ListView lvHistory = (ListView) findViewById(com.lospecchiodieva.droid.iching.R.id.lvHistory);
    lvHistory.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
        final ListView lvHistory = (ListView) findViewById(com.lospecchiodieva.droid.iching.R.id.lvHistory);
        HistoryEntry entry = (HistoryEntry) lvHistory.getItemAtPosition(position);
        IChingActivity thiz = IChingActivity.this;
        thiz.current.changing = entry.getChanging();
        thiz.current.changingManualIndex = 0;
        thiz.hex = entry.getHex();
        thiz.tHex = entry.getTHex();
        thiz.current.question = entry.getQuestion();
        thiz.current.mode = READ_DESC_MODE.ORACLE;
        thiz.current.tabIndex = 0;
        thiz.current.section = RemoteResolver.ICHING_REMOTE_SECTION_DESC;
        thiz.gotoReadDesc();
      }
    });

    renderOptionsMenu();

    // Trigger load history
    renderLoadHistory(null, new Runnable() {
      public void run() {
        if (historyList.size() == 0) {
          historyList.add(Utils.buildDummyHistoryEntry());
          // If default history file does not exist, create it
          DataPersister.saveHistory(historyList, IChingActivity.this);
          historyList.clear();
        }
      }
    });

    hexRow = 0;
    hex = new int[6];
    tHex = new int[6];
    current = new CurrentState();
  }

 /* private void setTabFontSize( TabHost tabHost)
  {
    final TabWidget tw = (TabWidget)tabHost.findViewById(android.R.id.tabs);
    for (int i = 0; i < tw.getChildCount(); ++i)
    {
      final View tabView = tw.getChildTabViewAt(i);
      final TextView tv = (TextView)tabView.findViewById(android.R.id.title);
      tv.setTextSize(2);
    }
    // lf.setPadding(0, 0, 0, 6);

  }*/


  /**
   * Move to the read description view
   */
  public void gotoReadDesc() {

    if (changingLinesEvaluator == null) {
      Integer evalType = (Integer) settings.get(SETTINGS_MAP.CHANGING_LINES_EVALUATOR);
      changingLinesEvaluator = ChangingLinesEvaluator.produce(evalType);
    }
    current.changing = changingLinesEvaluator.evaluate(hex, tHex);
    current.screen = READ_DESC_SCREEN.DEFAULT;

    setContentView(com.lospecchiodieva.droid.iching.R.layout.readdesc);

    final TabHost tabHost = (TabHost) findViewById(android.R.id.tabhost);
    tabHost.setup();


    switch (current.mode) {
      case ORACLE:
        if (current.changing == ChangingLinesEvaluator.ICHING_APPLY_CAST) {
          setupTab(tabHost, "tab_consult", com.lospecchiodieva.droid.iching.R.string.read_cast, com.lospecchiodieva.droid.iching.R.id.layReadDesc);
          setupTab(tabHost, "tab_changing", com.lospecchiodieva.droid.iching.R.string.read_changing, com.lospecchiodieva.droid.iching.R.id.layReadDesc);
        } else {
          setupTab(tabHost, "tab_consult", com.lospecchiodieva.droid.iching.R.string.read_cast, com.lospecchiodieva.droid.iching.R.id.layReadDesc);
          setupTab(tabHost, "tab_changing", com.lospecchiodieva.droid.iching.R.string.read_changing, com.lospecchiodieva.droid.iching.R.id.layReadDesc);
          setupTab(tabHost, "tab_future", com.lospecchiodieva.droid.iching.R.string.read_transformed, com.lospecchiodieva.droid.iching.R.id.layReadDesc);
        }
        break;
      case VIEW_HEX:
        setupTab(tabHost, "tab_consult", com.lospecchiodieva.droid.iching.R.string.read_cast, com.lospecchiodieva.droid.iching.R.id.layReadDesc);
        setupTab(tabHost, "tab_changing", com.lospecchiodieva.droid.iching.R.string.read_changing, com.lospecchiodieva.droid.iching.R.id.layReadDesc);
        break;
    }

    // Display current tab
    tabHost.getCurrentView().setVisibility(View.VISIBLE);
    final TextView tvDescTitle = (TextView) findViewById(com.lospecchiodieva.droid.iching.R.id.tvHexName);
    String hexMap = Utils.hexMap(hex);
    tvDescTitle.setText(Utils.getResourceByName(com.lospecchiodieva.droid.iching.R.string.class, "hex" + hexMap));
    tvDescTitle.setText(hexMap + " " + tvDescTitle.getText());

   // setTabFontSize(tabHost);

    renderTabs(tabHost);

    final List<String> listTabId = Arrays.asList(new String[]{
        "tab_consult", "tab_changing", "tab_future"
    });
    final OnTabChangeListener onTabChange = new OnTabChangeListener() {
      public void onTabChanged(String tabId) {
        if (current.tabIndex != listTabId.indexOf(tabId)) {
          current.section = RemoteResolver.ICHING_REMOTE_SECTION_DESC;
          current.tabIndex = listTabId.indexOf(tabId);
        }
        switch (current.tabIndex) {
          case TAB_READ_DESC_CAST_HEXAGRAM:
            renderReadDesc(hex);
            break;
          case TAB_READ_DESC_CHANGING_LINES:
            renderReadDescChanging(hex);
            break;
          case TAB_READ_DESC_TRANSFORMED_HEXAGRAM:
            renderReadDesc(tHex);
            break;
        }
      }
    };
    tabHost.setOnTabChangedListener(onTabChange);
    onTabChange.onTabChanged(listTabId.get(current.tabIndex));
    tabHost.getTabWidget().getChildAt(current.tabIndex).performClick();
  }

  /**
   * Move to the settings view
   */
  public void gotoSettings() {
    setContentView(com.lospecchiodieva.droid.iching.R.layout.settings);

    final ListView lvSettings = (ListView) findViewById(com.lospecchiodieva.droid.iching.R.id.lvSettings);

    List<SettingsEntry<?>> settingsList = new ArrayList<SettingsEntry<?>>();

    renderOptionsMenu();

    // Vibration
    settings.createOption(
        settingsList,
        SettingsEntry.VIBRATION,
        SettingsManager.SETTINGS_VALUES_MAP.get(SETTINGS_MAP.HAPTIC_FEEDBACK),
        SETTINGS_MAP.HAPTIC_FEEDBACK
    );
    // Divination method
    settings.createOption(
        settingsList,
        SettingsEntry.DIVINATION_METHOD,
        SettingsManager.SETTINGS_VALUES_MAP.get(SETTINGS_MAP.DIVINATION_METHOD),
        SETTINGS_MAP.DIVINATION_METHOD
    );
    // Changing lines
    settings.createOption(
        settingsList,
        SettingsEntry.CHLINES_EVALUATOR,
        SettingsManager.SETTINGS_VALUES_MAP.get(SETTINGS_MAP.CHANGING_LINES_EVALUATOR),
        SETTINGS_MAP.CHANGING_LINES_EVALUATOR
    );
    // Language
    settings.createOption(
        settingsList,
        SettingsEntry.LANGUAGE,
        SettingsManager.SETTINGS_VALUES_MAP.get(SETTINGS_MAP.LANGUAGE),
        SETTINGS_MAP.LANGUAGE
    );
    // Dictionary
    settings.createOption(
        settingsList,
        SettingsEntry.DICTIONARY,
        SettingsManager.SETTINGS_VALUES_MAP.get(SETTINGS_MAP.DICTIONARY),
        SETTINGS_MAP.DICTIONARY
    );
    // Storage
    settings.createOption(
        settingsList,
        SettingsEntry.STORAGE,
        SettingsManager.SETTINGS_VALUES_MAP.get(SETTINGS_MAP.STORAGE),
        SETTINGS_MAP.STORAGE
    );
    // Connection mode
    settings.createOption(
        settingsList,
        SettingsEntry.CONNECTION_MODE,
        SettingsManager.SETTINGS_VALUES_MAP.get(SETTINGS_MAP.CONNECTION_MODE),
        SETTINGS_MAP.CONNECTION_MODE
    );

    lvSettings.setAdapter(new ListItem2Adapter<SettingsEntry<?>>(this, settingsList) {
      @Override
      public String getText1(SettingsEntry<?> entry) {
        return Utils.s(Utils.getResourceByName(com.lospecchiodieva.droid.iching.R.string.class, entry.getOptionName()));
      }

      @Override
      public String getText2(SettingsEntry<?> entry) {
        return Utils.s(Utils.getResourceByName(com.lospecchiodieva.droid.iching.R.string.class, entry.getOptionName() + Utils.UNDERSCORE + entry.getOptionValue()));
      }
    });

    lvSettings.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> adapter, View view, final int settingIndex, long id) {
        @SuppressWarnings("unchecked")
        final SettingsEntry<Serializable> entry = (SettingsEntry<Serializable>) lvSettings.getItemAtPosition(settingIndex);
        Spinner spinner = (Spinner) findViewById(com.lospecchiodieva.droid.iching.R.id.spBacking);
        String[] optionsText = new String[entry.getOptionValues().size()];
        int count = 0;
        for (Serializable value : entry.getOptionValues()) {
          optionsText[count++] = Utils.s(Utils.getResourceByName(com.lospecchiodieva.droid.iching.R.string.class, entry.getOptionName() + Utils.UNDERSCORE + value.toString()));
        }
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
            getApplicationContext(),
            android.R.layout.simple_spinner_item,
            optionsText
        );
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);
        spinner.setSelection(entry.getOptionIndex());
        spinner.setPromptId(Utils.getResourceByName(com.lospecchiodieva.droid.iching.R.string.class, entry.getOptionName()));
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            final SETTINGS_MAP mapKey = SETTINGS_MAP.values()[settingIndex];
            final Serializable newValue = entry.getOptionValues().get(position);
            final Runnable renderSettingChange = new Runnable() {
              public void run() {
                renderSettingChanged(lvSettings, entry, mapKey,
                    newValue);
              }
            };

            performOnItemSelected(mapKey, newValue, renderSettingChange);
          }

          public void onNothingSelected(AdapterView<?> arg0) {
          }

          private void performOnItemSelected(final SETTINGS_MAP mapKey,
                                             final Serializable newValue,
                                             final Runnable renderSettingChange) {
            boolean changed = true;
            Context context = IChingActivity.this.getBaseContext();
            switch (mapKey) {
              case CHANGING_LINES_EVALUATOR:
                // Setting to null will reinit evaluator next time is needed
                changingLinesEvaluator = null;
                break;
              case LANGUAGE:
                Locale locale = new Locale(newValue.toString());
                settings.setLocale(locale);
                // Not breaking here
              case DICTIONARY:
                // Clear remote strings cache in case language or dictionary change
                RemoteResolver.clearCache();
                break;
              case STORAGE:
                // Switch the storage
                if (newValue.equals(Consts.STORAGE_SDCARD)) {
                  changed = DataPersister.useStorageSDCard(settings);
                } else if (newValue.equals(Consts.STORAGE_INTERNAL)) {
                  changed = DataPersister.useStorageInternal(settings, context);
                }
                break;
              case CONNECTION_MODE:
                changed = false;
                if (newValue.equals(Consts.CONNECTION_MODE_OFFLINE)) {
                  connectionManager.fromOnlineToOffline(IChingActivity.this, renderSettingChange);
                } else if (newValue.equals(Consts.CONNECTION_MODE_ONLINE)) {
                  connectionManager.fromOfflineToOnline(IChingActivity.this, renderSettingChange);
                }
                break;
            }

            if (changed) {
              renderSettingChange.run();
            }
          }

          private void renderSettingChanged(
              final ListView lvSettings,
              final SettingsEntry<Serializable> entry,
              SETTINGS_MAP mapKey, Serializable newValue) {
            entry.setOptionValue(newValue);
            settings.put(mapKey, newValue);

            ((BaseAdapter) lvSettings.getAdapter()).notifyDataSetChanged();
            lvSettings.invalidateViews();

            settings.save(IChingActivity.this);
          }
        });
        spinner.performClick();
      }
    });
  }

  /**
   * onClick handler for the init (main) view
   *
   * @param view The event target
   */
  public void onClickConsultBtn(View view) {
    switch (view.getId()) {
      case com.lospecchiodieva.droid.iching.R.id.btnQuestion:
        EditText etQuestion = (EditText) findViewById(com.lospecchiodieva.droid.iching.R.id.etQuestion);
        current.question = etQuestion.getText().toString();

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etQuestion.getWindowToken(), 0);

        if (current.question.isEmpty()) {
          AlertDialog alertDialog = new AlertDialog.Builder(IChingActivity.this).create();
          alertDialog.setMessage(Utils.s(com.lospecchiodieva.droid.iching.R.string.intro_noquestion_alert));
          alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, Utils.s(com.lospecchiodieva.droid.iching.R.string.yes), new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
              gotoConsult();
            }
          });
          alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, Utils.s(com.lospecchiodieva.droid.iching.R.string.no), new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
          });
          alertDialog.show();
        } else {
          gotoConsult();
        }

        break;
    }
  }

  /**
   * onClick handler for the coins in the consult view. Generates the hexagram
   *
   * @param view Not used
   */
  public void onClickGenerateRow(View view) {
    int coinsValue = 0;
    for (int i = 0; i < 3; i++) {
      double rnd = Math.random();
      if (rnd < 0.5) {
        coinsValue += 2;
      } else {
        coinsValue += 3;
      }
    }

    generateRow(coinsValue);
  }

  /**
   * Callback for a context menu voice selection
   *
   * @param item The selected menu voice
   * @return true
   */
  @Override
  public boolean onContextItemSelected(final MenuItem item) {
    final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
        .getMenuInfo();
    ListView lvHistory = (ListView) findViewById(com.lospecchiodieva.droid.iching.R.id.lvHistory);
    final BaseAdapter listAdapter = (BaseAdapter) lvHistory.getAdapter();
    final View tvHistory = (View) findViewById(com.lospecchiodieva.droid.iching.R.id.tvHistory);
    contextSelectDialog = new AlertDialog.Builder(IChingActivity.this).create();
    switch (item.getItemId()) {
      case ContextMenuItem.HISTORY_MOVE_ENTRY:
        final List<String> historyNames = DataPersister.getHistoryNames();
        historyNames.remove(DataPersister.getSelectedHistoryName());
        if (historyNames.size() > 0) {
          Dialog dialog = buildItemSelectionDialog(
              historyNames.toArray(new String[historyNames.size()]),
              Utils.s(com.lospecchiodieva.droid.iching.R.string.item_select_dest_history),
              new OnClickListener() {
                public void onClick(final DialogInterface dialog, int index) {
                  final HistoryEntry entry = historyList.remove(info.position);
                  final String targetHistory = historyNames.get(index);
                  DataPersister.saveHistory(historyList, IChingActivity.this);
                  DataPersister.setSelectedHistory(targetHistory, Utils.EMPTY_STRING, true);
                  IChingActivity.this.renderLoadHistory(new Runnable() {
                    public void run() {
                      historyList.add(entry);
                      Utils.sortHistoryList(historyList);
                      DataPersister.saveHistory(historyList, IChingActivity.this);
                      renderLoadHistory(null, null);
                      dialog.dismiss();
                    }
                  }, new Runnable() {
                    public void run() {
                      DataPersister.revertSelectedHistory();
                      // Reload history and put back the removed entry
                      renderLoadHistory(null, null);
                      historyList.add(entry);
                      Utils.sortHistoryList(historyList);
                      DataPersister.saveHistory(historyList, IChingActivity.this);
                      renderLoadHistory(null, null);
                      dialog.dismiss();
                    }
                  });
                }
              });

          dialog.show();
        } else {
          showToast(Utils.s(com.lospecchiodieva.droid.iching.R.string.history_no_destination));
        }
        break;
      case ContextMenuItem.HISTORY_DELETE_ENTRY:
        String question = historyList.get(info.position).getQuestion();
        if (question.isEmpty()) {
          question = Utils.s(com.lospecchiodieva.droid.iching.R.string.contextmenu_noquestion);
        }
        contextSelectDialog.setTitle(question);
        contextSelectDialog.setMessage(Utils.s(com.lospecchiodieva.droid.iching.R.string.contextmenu_history_erase_entry));
        contextSelectDialog.setButton(DialogInterface.BUTTON_POSITIVE,
            Utils.s(com.lospecchiodieva.droid.iching.R.string.yes),
            new OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                historyList.remove(info.position);
                if (historyList.size() == 0) {
                  tvHistory.setVisibility(View.GONE);
                }
                DataPersister.saveHistory(historyList, IChingActivity.this);
                listAdapter.notifyDataSetChanged();
              }
            });
        contextSelectDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
            Utils.s(com.lospecchiodieva.droid.iching.R.string.no),
            new OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
              }
            });
        contextSelectDialog.show();
        break;
      case ContextMenuItem.HISTORY_DELETE_ALL:
        contextSelectDialog.setTitle(DataPersister.getSelectedHistoryName());
        contextSelectDialog.setMessage(Utils.s(com.lospecchiodieva.droid.iching.R.string.contextmenu_history_erase));
        contextSelectDialog.setButton(DialogInterface.BUTTON_POSITIVE,
            Utils.s(com.lospecchiodieva.droid.iching.R.string.yes),
            new OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                historyList.clear();
                tvHistory.setVisibility(View.GONE);
                DataPersister.saveHistory(historyList, IChingActivity.this);
                listAdapter.notifyDataSetChanged();
              }
            });
        contextSelectDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
            Utils.s(com.lospecchiodieva.droid.iching.R.string.no),
            DEFAULT_HISTORY_REVERT_DIALOG_BUTTON);
        contextSelectDialog.show();
        break;
      case ContextMenuItem.HISTORY_REMOVE:
        renderLoadHistory(new Runnable() {
          public void run() {
            contextSelectDialog.setTitle(DataPersister.getSelectedHistoryName());
            contextSelectDialog.setMessage(Utils.s(com.lospecchiodieva.droid.iching.R.string.contextmenu_history_remove));
            contextSelectDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                Utils.s(com.lospecchiodieva.droid.iching.R.string.yes),
                new OnClickListener() {
                  public void onClick(DialogInterface dialog,
                                      int which) {
                    DataPersister.removeHistory(IChingActivity.this);
                    DEFAULT_HISTORY_REVERT_TASK.run();
                  }
                });
            contextSelectDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                Utils.s(com.lospecchiodieva.droid.iching.R.string.no),
                new OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                    DEFAULT_HISTORY_REVERT_TASK.run();
                  }
                });
            contextSelectDialog.show();
          }
        }, DEFAULT_HISTORY_REVERT_TASK);
        break;
      case ContextMenuItem.HISTORY_RENAME:
        renderLoadHistory(new Runnable() {
            public void run() {
              contextSelectDialog.setMessage(Utils.s(com.lospecchiodieva.droid.iching.R.string.contextmenu_history_rename));
              final EditText input = new EditText(IChingActivity.this);
              input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
              input.setHint(DataPersister.getSelectedHistoryName());
              contextSelectDialog.setView(input);
              contextSelectDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                  Utils.s(android.R.string.ok),
                  new OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int which) {
                      String historyNewName = input.getText().toString();
                      if (!historyNewName.equals(Utils.EMPTY_STRING)) {
                        DataPersister.renameHistory(historyList, IChingActivity.this, historyNewName);
                      } else {
                        DataPersister.revertSelectedHistory();
                      }
                      renderLoadHistory(null, null);
                    }
                  });
              contextSelectDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                  Utils.s(com.lospecchiodieva.droid.iching.R.string.cancel),
                  DEFAULT_HISTORY_REVERT_DIALOG_BUTTON);
              contextSelectDialog.show();
            }
          },
          DEFAULT_HISTORY_REVERT_TASK);
        break;
    }
    return true;
  }

  /**
   * Called when the activity is first created.
   *
   * @param savedInstanceState The saved state
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Utils.setContext(getApplicationContext());

    loadSettings();

      SettingsManager settings = getSettingsManager();
      final String dictionary = (String) settings.get(SETTINGS_MAP.DICTIONARY);
      final String lang = (String) settings.get(SETTINGS_MAP.LANGUAGE);

      final String connectionMode = (String) settings.get(SETTINGS_MAP.CONNECTION_MODE);

      if (Consts.CONNECTION_MODE_ONLINE.equals(connectionMode)) {
          HexSectionDataSource dataSource = new HexSectionDataSource(getApplicationContext());
          dataSource.open();

          for (int i = 0; i < Consts.HEX_COUNT; i++) {
              String hex = getHexFromIndex(i + 1);
              dataSource.deleteHexSections(hex, dictionary, lang);
          }

          dataSource.close();
      }
  }


    private String getHexFromIndex(int hexIndex) {
        String hex = (hexIndex < 10 ? "0" : Utils.EMPTY_STRING) + hexIndex;
        return hex;
    }

  /**
   * Create a context menu
   */
  @Override
  public void onCreateContextMenu(final ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

    switch (v.getId()) {
      case com.lospecchiodieva.droid.iching.R.id.lvHistory:
        HistoryEntry entry = historyList.get(info.position);
        String question = entry.getQuestion();
        if (question.isEmpty()) {
          question = Utils.s(com.lospecchiodieva.droid.iching.R.string.contextmenu_noquestion);
        }
        menu.setHeaderTitle(question);
        menu.add(0, ContextMenuItem.HISTORY_MOVE_ENTRY, 0, com.lospecchiodieva.droid.iching.R.string.contextmenu_move_entry);
        menu.add(0, ContextMenuItem.HISTORY_DELETE_ENTRY, 1, com.lospecchiodieva.droid.iching.R.string.contextmenu_delete_entry);
        menu.add(0, ContextMenuItem.HISTORY_DELETE_ALL, 2, com.lospecchiodieva.droid.iching.R.string.contextmenu_delete_all);
        break;
      case com.lospecchiodieva.droid.iching.R.id.elSelectHistory:
        final TextView tvChild = (TextView) v;
        final String historyName = tvChild.getText().toString();
        if (!historyName.equals(DataPersister.ICHING_HISTORY_PATH_FILENAME_DEFAULT)) {
          menu.setHeaderTitle(historyName);
          menu.add(0, ContextMenuItem.HISTORY_RENAME, 0, com.lospecchiodieva.droid.iching.R.string.contextmenu_rename_entry);
          menu.add(0, ContextMenuItem.HISTORY_REMOVE, 1, com.lospecchiodieva.droid.iching.R.string.contextmenu_remove_entry);
          DataPersister.setSelectedHistory(historyName, Utils.EMPTY_STRING, true);
        }
        break;
    }
  }

  /**
   * Create an option menu for the "About" section and other stuff
   */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(com.lospecchiodieva.droid.iching.R.menu.menu, menu);
    optionsMenu = menu;

    renderOptionsMenu();

    return true;
  }

  /**
   * Implementation of the onKeyDown method to deal with back button
   *
   * @param keyCode The pressed key code
   * @param event   The key down event
   * @return true if the event has been consumed, false otherwise
   */
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (current.viewId == com.lospecchiodieva.droid.iching.R.layout.main) {
        onBackPressed();
        System.exit(0);
        return true;
      } else {
        current.question = Utils.EMPTY_STRING;
        gotoMain();
        return true;
      }
    }
    return false;
  }

  /**
   * Respond to the option menu voices selection
   */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final AlertDialog alertDialog = new AlertDialog.Builder(IChingActivity.this).create();

    TextView tvMessage = new TextView(getApplicationContext());
    tvMessage.setBackgroundColor(getResources().getColor(android.R.color.background_dark));
    tvMessage.setTextColor(getResources().getColor(android.R.color.primary_text_dark));
    tvMessage.setTextSize(13);
    tvMessage.setPadding(5, 5, 5, 5);

    alertDialog.setView(tvMessage);
    alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, Utils.s(android.R.string.ok), new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        alertDialog.dismiss();
      }
    });

    switch (item.getItemId()) {
      case com.lospecchiodieva.droid.iching.R.id.omSettings:
        gotoSettings();
        break;
      case com.lospecchiodieva.droid.iching.R.id.omViewHex:
        String[] hexArray = new String[Consts.HEX_COUNT];
        for (int i = 0; i < hexArray.length; i++) {
          String index = (i + 1 < 10 ? "0" : Utils.EMPTY_STRING) + (i + 1);
          int entry = Utils.getResourceByName(com.lospecchiodieva.droid.iching.R.string.class, "hex" + index);
          hexArray[i] = (i + 1) + " " + Utils.s(entry);
        }

        Dialog dialog = buildTrigramSelectionDialog(
            hexArray,
            Utils.s(com.lospecchiodieva.droid.iching.R.string.item_select_hex),
            new OnClickListener() {
              public void onClick(DialogInterface dialog, int index) {
                hex = Utils.invHexMap(index + 1);
                current = new CurrentState();

                gotoReadDesc();
                dialog.dismiss();
              }
            }
        );

        dialog.show();
        break;
      case com.lospecchiodieva.droid.iching.R.id.omAlgo:
        alertDialog.setMessage(Utils.s(com.lospecchiodieva.droid.iching.R.string.options_algo));
        tvMessage.setText(Html.fromHtml(Utils.s(com.lospecchiodieva.droid.iching.R.string.options_algo_message)));
        alertDialog.show();
        break;
      case com.lospecchiodieva.droid.iching.R.id.omReferences:
        alertDialog.setMessage(Utils.s(com.lospecchiodieva.droid.iching.R.string.options_references));
        tvMessage.setText(Html.fromHtml(Utils.s(com.lospecchiodieva.droid.iching.R.string.options_references_message)));
        alertDialog.show();
        break;
      case com.lospecchiodieva.droid.iching.R.id.omAbout:
        alertDialog.setMessage(Utils.s(com.lospecchiodieva.droid.iching.R.string.options_about));
        tvMessage.setText(Html.fromHtml(Utils.s(com.lospecchiodieva.droid.iching.R.string.options_about_message)));
        alertDialog.show();
        break;
      case com.lospecchiodieva.droid.iching.R.id.omReadDescEdit:
        renderEditHexSection();
        break;
      case com.lospecchiodieva.droid.iching.R.id.omReadDescUndo:
        renderResetHexSection();
        break;
      case com.lospecchiodieva.droid.iching.R.id.omReadDescShare:
        performShare();
        break;
    }
    return true;
  }

  /**
   * Update the option menu for the "About" section and other stuff
   */
  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    final String dictionary = (String) getSettingsManager().get(SETTINGS_MAP.DICTIONARY);
    final MenuItem omSettings = menu.findItem(com.lospecchiodieva.droid.iching.R.id.omSettings);
    omSettings.setTitle(com.lospecchiodieva.droid.iching.R.string.options_settings);
    final MenuItem omViewHex = menu.findItem(com.lospecchiodieva.droid.iching.R.id.omViewHex);
   /* if (dictionary.equals(Consts.DICTIONARY_CUSTOM)) {
      omViewHex.setTitle(com.lospecchiodieva.droid.iching.R.string.options_view_edit_hex);
    } else {*/
      omViewHex.setTitle(com.lospecchiodieva.droid.iching.R.string.options_view_hex);
   // }
    final MenuItem omReferences = menu.findItem(com.lospecchiodieva.droid.iching.R.id.omReferences);
    omReferences.setTitle(com.lospecchiodieva.droid.iching.R.string.options_references);
    final MenuItem omAlgo = menu.findItem(com.lospecchiodieva.droid.iching.R.id.omAlgo);
    omAlgo.setTitle(com.lospecchiodieva.droid.iching.R.string.options_algo);
    final MenuItem omAbout = menu.findItem(com.lospecchiodieva.droid.iching.R.id.omAbout);
    omAbout.setTitle(com.lospecchiodieva.droid.iching.R.string.options_about);
    return true;
  }

  /**
   * Called when the activity is restored.
   *
   * @param savedInstanceState The saved state
   */
  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);

    current.viewId = savedInstanceState.getInt("currentView");
    current.question = savedInstanceState.getString("question");

    hexRow = savedInstanceState.getInt("hexRow");
    current.changing = savedInstanceState.getInt("changing");
    hex = savedInstanceState.getIntArray("hex");
    tHex = savedInstanceState.getIntArray("tHex");

    current.mode = READ_DESC_MODE.valueOf(savedInstanceState.getString("mode"));

    setCurrentSection(current.changing);
    setCurrentHex(hex);
  }

  /**
   * Called when the activity is suspended.
   *
   * @param savedInstanceState The saved state
   */
  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    savedInstanceState.putInt("currentView", current.viewId);
    savedInstanceState.putString("question", current.question);

    savedInstanceState.putInt("hexRow", hexRow);
    savedInstanceState.putInt("changing", current.changing);
    savedInstanceState.putIntArray("hex", hex);
    savedInstanceState.putIntArray("tHex", tHex);

    READ_DESC_MODE mode = current.mode == null ? READ_DESC_MODE.ORACLE : current.mode;
    savedInstanceState.putString("mode", String.valueOf(mode));

    super.onSaveInstanceState(savedInstanceState);
  }

  /**
   * Wrapper method to set the content view after storing it
   *
   * @param resId A layout view resource identifier
   */
  @Override
  public void setContentView(int resId) {
    current.viewId = resId;
    super.setContentView(current.viewId);
  }

  @Override
  protected void onResume() {
    super.onResume();

    loadSettings();
    switch (current.viewId) {
      case com.lospecchiodieva.droid.iching.R.layout.consult:
        gotoConsult();
        break;
      case com.lospecchiodieva.droid.iching.R.layout.readdesc:
        gotoReadDesc();
        break;
      case com.lospecchiodieva.droid.iching.R.layout.settings:
        gotoSettings();
        break;
      default:
        gotoMain();
    }
  }

  private void prepareDivinationMethod() {
    Resources res = getResources();
    int divinationMethod = (Integer) getSettingsManager().get(SETTINGS_MAP.DIVINATION_METHOD);
    switch (divinationMethod) {
      case Consts.DIVINATION_METHOD_COINS_MANUAL:
        Random rnd = new Random();
        final AnimCoin coin1 = new AnimCoin((ImageView) findViewById(com.lospecchiodieva.droid.iching.R.id.picCoin01), res, rnd.nextInt(2) + 2);
        final AnimCoin coin2 = new AnimCoin((ImageView) findViewById(com.lospecchiodieva.droid.iching.R.id.picCoin02), res, rnd.nextInt(2) + 2);
        final AnimCoin coin3 = new AnimCoin((ImageView) findViewById(com.lospecchiodieva.droid.iching.R.id.picCoin03), res, rnd.nextInt(2) + 2);
        OnTouchListener coinTouchListener = new OnTouchListener() {
          public boolean onTouch(View v, MotionEvent event) {
            hex[hexRow] = coin1.getCoinValue() + coin2.getCoinValue() + coin3.getCoinValue();
            renderRow(hexRow, hex[hexRow], true, null, null);
            return true;
          }
        };
        coin1.setOnTouchListener(coinTouchListener);
        coin2.setOnTouchListener(coinTouchListener);
        coin3.setOnTouchListener(coinTouchListener);
        coinTouchListener.onTouch(null, null);
        final GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

          private static final int SWIPE_MIN_DISTANCE = 120;
          private static final int SWIPE_THRESHOLD_VELOCITY = 200;

          @Override
          public boolean onDown(MotionEvent event) {
            return true;
          }

          @Override
          public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            int coinSum = coin1.getCoinValue() + coin2.getCoinValue() + coin3.getCoinValue();
            if (e1.getY() - e2.getY() > SWIPE_MIN_DISTANCE && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY &&
                hexRow < Consts.HEX_LINES_COUNT) {
              generateRow(coinSum);
              if (hexRow < Consts.HEX_LINES_COUNT) {
                hex[hexRow] = coinSum;
                renderRow(hexRow, hex[hexRow], true, null, null);
              }
            } else if (e2.getY() - e1.getY() > SWIPE_MIN_DISTANCE && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
              eraseRow(coinSum);
            }
            return super.onFling(e1, e2, velocityX, velocityY);
          }
        });
        View layConsult = findViewById(com.lospecchiodieva.droid.iching.R.id.layConsult);
        layConsult.setOnTouchListener(new OnTouchListener() {
          public boolean onTouch(View v, MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
          }
        });
        TextView tvInstructions = (TextView) findViewById(com.lospecchiodieva.droid.iching.R.id.tvInstructions);
        tvInstructions.setText(Utils.s(com.lospecchiodieva.droid.iching.R.string.consult_tapcoins_manual));
        break;
      default:
        new AnimCoin((ImageView) findViewById(com.lospecchiodieva.droid.iching.R.id.picCoin01), res);
        new AnimCoin((ImageView) findViewById(com.lospecchiodieva.droid.iching.R.id.picCoin02), res);
        new AnimCoin((ImageView) findViewById(com.lospecchiodieva.droid.iching.R.id.picCoin03), res);
    }
  }

  private void eraseRow(int coinSum) {
    if (hexRow > 0) {
      hex[hexRow--] = -1;
    }
    if (hexRow < Consts.HEX_LINES_COUNT) {
      hex[hexRow] = coinSum;
    }
    for (int i = 0; i < Consts.HEX_LINES_COUNT; i++) {
      renderRow(i, hex[i], true, null, null);
    }
    if (Utils.mask((Integer) settings.get(SETTINGS_MAP.HAPTIC_FEEDBACK), Consts.HAPTIC_FEEDBACK_ON_THROW_COINS)) {
      Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      v.vibrate(300);
    }
  }

  private void generateRow(int coinsValue) {
    hex[hexRow] = coinsValue;
    renderRow(hexRow++, coinsValue, true, null, null);

    if (hexRow >= Consts.HEX_LINES_COUNT) {
      gotoConsult();
    }

    if (Utils.mask((Integer) settings.get(SETTINGS_MAP.HAPTIC_FEEDBACK), Consts.HAPTIC_FEEDBACK_ON_THROW_COINS)) {
      Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      v.vibrate(300);
    }
  }

  /**
   * Load settings from sd card. If none is found, default apply
   */
  private void loadSettings() {
    try {
      if (settings == null) {
        settings = new SettingsManager(getApplicationContext());
      }
      settings.load();
      if (current.viewId != null) {
        setContentView(current.viewId);
      }
      invalidateOptionsMenu();
    } catch (FileNotFoundException e) {
      settings.resetDefaults(false);
    } catch (IOException e) {
      settings.resetDefaults(true);
      AlertDialog alertDialog = new AlertDialog.Builder(IChingActivity.this).create();
      alertDialog.setMessage(Utils.s(com.lospecchiodieva.droid.iching.R.string.options_unavailable));
      alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, Utils.s(android.R.string.ok), new OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
        }
      });
      alertDialog.show();
    }
  }

  /**
   * Internal method to add a tab to an host
   *
   * @param tabHost The host
   * @param tag     A tag for the tab
   * @param indId   The tab title resource identifier
   * @param resId   The content layout resource identifier
   */
  private void setupTab(TabHost tabHost, String tag, int indId, int resId) {




    tabHost.addTab(
        tabHost.newTabSpec(tag)
            .setIndicator(Utils.s(indId))
            .setContent(resId)
    );

  }

  /**
   * Unique identifiers for the context menu voices *
   */
  private class ContextMenuItem {
    private static final int HISTORY_MOVE_ENTRY = 1;
    private static final int HISTORY_DELETE_ENTRY = 2;
    private static final int HISTORY_DELETE_ALL = 3;
    private static final int HISTORY_REMOVE = 4;
    private static final int HISTORY_RENAME = 5;
  }
}