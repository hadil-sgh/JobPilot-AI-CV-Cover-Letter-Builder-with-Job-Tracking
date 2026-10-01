[#-- ATS Classic motivation letter: same style file as the CV so both match visually. --]
\documentclass[[=opt.fontSize]]{article}
\usepackage[a4paper, margin=2.2cm]{geometry}
\newcommand{\jpsectionbefore}{1.8ex}\newcommand{\jpsectionafter}{0.8ex}\newcommand{\jpitemsep}{1pt}
\usepackage{jobpilot-classic}
\definecolor{accent}{HTML}{[=opt.accentHex]}
\hypersetup{pdftitle={[=doc.meta.title]}, pdfauthor={[=doc.meta.author]}, pdfsubject={[=doc.meta.subject]}, pdflang={[=doc.meta.lang]}, pdfcreator={JobPilot}}
\setlength{\parskip}{0.9em}

\begin{document}

{\sffamily\bfseries\Large\color{accent} [=doc.header.fullName]}\par
\vspace{-0.6em}{\small [#list doc.header.contact as c][=c][#sep]\enspace|\enspace{}[/#sep][/#list]}\par
{\color{accent}\rule{\linewidth}{0.4pt}}

\begin{flushright}
[=doc.company]\par
[=doc.date]
\end{flushright}

[#if doc.subject?has_content]\textbf{[=doc.subject]}\par[/#if]

[=doc.greeting]\par

[#list doc.paragraphs as p]
[=p]\par

[/#list]
[=doc.closing]\par
\vspace{1.2em}
\textbf{[=doc.header.fullName]}

\end{document}
