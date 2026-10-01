[#-- ATS Classic CV. FreeMarker square-bracket syntax; every [=value] is LaTeX-escaped automatically. --]
\documentclass[[=opt.fontSize]]{article}
[#switch opt.density]
[#case "compact"]\usepackage[a4paper, margin=1.3cm]{geometry}
\newcommand{\jpsectionbefore}{1.2ex}\newcommand{\jpsectionafter}{0.6ex}\newcommand{\jpitemsep}{0pt}\newcommand{\jpentrygap}{0.5ex}
[#break]
[#case "airy"]\usepackage[a4paper, margin=2cm]{geometry}
\newcommand{\jpsectionbefore}{2.4ex}\newcommand{\jpsectionafter}{1.2ex}\newcommand{\jpitemsep}{2pt}\newcommand{\jpentrygap}{1.4ex}
[#break]
[#default]\usepackage[a4paper, margin=1.6cm]{geometry}
\newcommand{\jpsectionbefore}{1.8ex}\newcommand{\jpsectionafter}{0.8ex}\newcommand{\jpitemsep}{1pt}\newcommand{\jpentrygap}{0.9ex}
[/#switch]
\usepackage{jobpilot-classic}
\definecolor{accent}{HTML}{[=opt.accentHex]}
\hypersetup{pdftitle={[=doc.meta.title]}, pdfauthor={[=doc.meta.author]}, pdfsubject={[=doc.meta.subject]}, pdfkeywords={[=doc.meta.keywords]}, pdflang={[=doc.meta.lang]}, pdfcreator={JobPilot}}

\begin{document}

\begin{center}
{\sffamily\bfseries\LARGE [=doc.header.fullName]}\par
[#if doc.header.headline?has_content]\vspace{2pt}{\large [=doc.header.headline]}\par[/#if]
[#-- "{}" ends the separator macro so it cannot merge with the next word (\enspaceTunis). --]
\vspace{3pt}{\small [#list doc.header.contact as c][=c][#sep]\enspace|\enspace{}[/#sep][/#list]}\par
[#if doc.header.links?has_content]{\small [#list doc.header.links as l]\href{[=l.url]}{[=l.text]}[#sep]\enspace|\enspace{}[/#sep][/#list]}\par[/#if]
\end{center}

[#list doc.sections as s]
\section*{[=s.title]}
[#switch s.key]
[#case "summary"]
[=s.text]\par
[#break]
[#case "skills"]
[#list s.groups as g]\textbf{[=g.group]:} [=g.items?join(", ")]\par
[/#list]
[#break]
[#case "languages"]
[=s.text]\par
[#break]
[#default]
[#list s.entries as e]
\jpentry{[=e.heading]}{[=e.dates]}
[#if e.detail?has_content]\jpdetail{[=e.detail]}[/#if]
[#if e.bullets?has_content]
\begin{itemize}
[#list e.bullets as b]  \item [=b]
[/#list]\end{itemize}
[/#if]
[#sep]\vspace{\jpentrygap}[/#sep]
[/#list]
[/#switch]
[/#list]

\end{document}
