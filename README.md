# CrySentry: A Tai-e Plugin for Detecting Java Crypto API Misuse

CrySentry is a static analysis tool for detecting **Java cryptographic API misuses** based on Tai-e whole-program pointer analysis and taint analysis.



---

## Why CrySentry?


"Cryptography is a vital technology that underpins the security of information in computer networks".
As the cornerstone of modern security frameworks, cryptography is crucial for protecting sensitive data, safeguarding transactions and communications, authenticating identities, and more.


The Java platform provides cryptography-related functionalities through the Java Cryptography Architecture (JCA) and the Java Secure Socket Extension (JSSE), enabling encryption, key generation, secure communication, and other tasks.

However, the complexity of crytpo API usage, combined with developers' general lack of security knowledge, often results in cryptographic API (crypto API) misuses, leading to security vulnerabilities such as sensitive data leaks, broken authentication, and man-in-the-middle attacks.

Existing crypto API misuse detectors still suffer from false negatives caused by restricted interprocedural tracking and insufficient alias analysis, as well as false positives introduced by coarse-grained detection rules.

On four widely used crypto API misuse benchmark suites: MUBench, OWASP Benchmark, Apache Crypto API Bench, and Crypto API Bench, **CrySentry** achieves consistently better soundness (**97.5% average recall**) and precision (**96.7% precision**), compared with state-of-the-art tools including CryptoGuard, CrySL, and FindSecBugs.

<div align="center">
  <img src="fig/evaluation.png" width="430" height="330" alt="Evaluation Overview">
  <p><strong>Figure 1: Evaluation overview comparing CrySentry with existing tools.</strong></p>
</div>



---

## Overview

A major and challenging class of crypto API misuse involves insecure crypto information being used in security-sensitive API calls. CrySentry is designed to detect such misuses using Tai-e’s whole-program pointer analysis and taint analysis framework.

<div align="center">
  <img src="fig/overview.png" width="900" alt="CrySentry Architecture">
  <p><strong>Figure 2: Overview of CrySentry.</strong></p>
</div>



CrySentry models:

- crypto-related information as **sources**,
- security-sensitive crypto APIs as **sinks**,
- and tracks the propagation of crypto information through Tai-e pointer analysis and taint analysis.
- For the collected crypto-related taint flows, CrySentry applies configurable misuse detectors to determine whether the flows indicate potential security risks or vulnerabilities (e.g., insecure crypto information in security-sensitive API calls).

The current implementation supports multiple classes of crypto API misuse detection, including:

- insecure algorithms,
- predictable cryptographic sources,
- invalid iteration counts or key sizes,
- insecure methods,
- missing security checks,
- and combined misuse patterns.

Users can flexibly configure crypto sources, crypto sinks, propagation rules, and misuse detection rules, allowing CrySentry to be adapted to different APIs, frameworks, and security policies.

---

## Usage

TODO

---

## Repository layout

TODO


---


## License

This project will be released under an open-source license.


