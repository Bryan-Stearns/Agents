/////////////////////////////////////////////////////////////////
// timers command file.
//
// Author: Jonathan Voigt, voigtjr@gmail.com
// Date  : 2004
//
/////////////////////////////////////////////////////////////////

#include <portability.h>

#include "sml_Utils.h"
#include "cli_CommandLineInterface.h"

#include "cli_Commands.h"
#include "cli_CLIError.h"

#include "sml_Names.h"

#include "sml_KernelSML.h"
#include "sml_KernelHelpers.h"
#include "gsysparam.h"

using namespace cli;
using namespace sml;

bool CommandLineInterface::ParseTimers(std::vector<std::string>& argv) {
	Options optionsData[] = {
		{'e', "enable",		0},
		{'d', "disable",	0},
		{'d', "off",		0},
		{'e', "on",			0},
		{0, 0, 0}
	};

	bool print = true;
	bool setting = false;	// enable or disable timers, default of false ignored

	for (;;) {
		if (!ProcessOptions(argv, optionsData)) return false;
		if (m_Option == -1) break;

		switch (m_Option) {
			case 'e':
				print = false;
				setting = true; // enable timers
				break;
			case 'd':
				print = false;
				setting = false; // disable timers
				break;
			default:
				return SetError(CLIError::kGetOptError);
		}
	}

	// No non-option arguments
	if (m_NonOptionArguments) return SetError(CLIError::kTooManyArgs);

	return DoTimers(print ? 0 : &setting);
}

bool CommandLineInterface::DoTimers(bool* pSetting) {
	// Attain the evil back door of doom, even though we aren't the TgD, because we'll probably need it
	sml::KernelHelpers* pKernelHack = m_pKernelSML->GetKernelHelpers() ;

	if (pSetting) {
		// set, don't print
		pKernelHack->SetSysparam(m_pAgentSML, TIMERS_ENABLED, *pSetting);

	} else {
		// print current setting
		// BUGBUG: Use Get/SetSysparam because it fires an event!
		const long* pSysparams = pKernelHack->GetSysparams(m_pAgentSML);

		if (m_RawOutput) {
			m_Result << (pSysparams[TIMERS_ENABLED] ? "Timers are enabled." : "Timers are disabled.");
		} else {
			// adds <arg name="timers">true</arg> (or false) if the timers are
			// enabled (or disabled)
			AppendArgTagFast(sml_Names::kParamTimers, sml_Names::kTypeBoolean, pSysparams[TIMERS_ENABLED] ? sml_Names::kTrue : sml_Names::kFalse);
		}
	}
	return true;
}

